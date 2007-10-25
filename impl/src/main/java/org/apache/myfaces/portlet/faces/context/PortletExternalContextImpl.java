/* Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.apache.myfaces.portlet.faces.context;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import java.net.MalformedURLException;
import java.net.URL;

import java.security.Principal;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.faces.FacesException;
import javax.faces.context.ExternalContext;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletConfig;
import javax.portlet.PortletContext;
import javax.portlet.PortletException;
import javax.portlet.PortletRequest;
import javax.portlet.PortletRequestDispatcher;
import javax.portlet.PortletResponse;
import javax.portlet.PortletURL;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import org.apache.myfaces.portlet.faces.util.map.LocalesIterator;
import org.apache.myfaces.portlet.faces.util.map.PortletApplicationMap;
import org.apache.myfaces.portlet.faces.util.map.PortletInitParameterMap;
import org.apache.myfaces.portlet.faces.util.map.PortletRequestHeaderMap;
import org.apache.myfaces.portlet.faces.util.map.PortletRequestHeaderValuesMap;
import org.apache.myfaces.portlet.faces.util.map.PortletRequestHeaders;
import org.apache.myfaces.portlet.faces.util.map.PortletRequestMap;
import org.apache.myfaces.portlet.faces.util.map.PortletRequestParameterMap;
import org.apache.myfaces.portlet.faces.util.map.PortletRequestParameterValuesMap;
import org.apache.myfaces.portlet.faces.util.map.PortletSessionMap;
import org.apache.myfaces.portlet.faces.util.QueryString;
import org.apache.myfaces.portlet.faces.util.URLUtils;

import java.util.Vector;

import javax.faces.application.ViewHandler;

import javax.portlet.faces.Bridge;

import javax.portlet.faces.BridgeDefaultViewNotSpecifiedException;

/**
 * This implementation of {@link ExternalContext} is specific to the portlet implementation.
 * 
 * Methods of interests are: - encodeActionURL - redirect
 */
public class PortletExternalContextImpl extends ExternalContext
{

  public static final String    FACES_MAPPING_ATTRIBUTE            = "org.apache.myfaces.portlet.faces.context.facesMapping";

  public static final String    RENDER_POLICY_ATTRIBUTE            = Bridge.BRIDGE_PACKAGE_PREFIX
                                                                     + "." + Bridge.RENDER_POLICY;

  // Query parameter to store the original viewId in the query string
  public static final String    VIEW_ID_QUERY_PARAMETER            = "_VIEW_ID";

  // Render parameter to store the viewId
  public static final String    ACTION_ID_PARAMETER_NAME           = "_ACTION_ID";

  public static final String    RESOURCE_METHOD_QUERY_PARAMETER    = "_xResourceMethod";
  public static final String    RESOURCE_URL_QUERY_PARAMETER       = "_xResourceUrl";
  public static final String    FACES_RESOURCE_QUERY_PARAMETER     = "_xFacesResource";
  public static final String    PROCESS_AS_RENDER_QUERY_PARAMETER  = "_xProcessAsRender";
  public static final String    REQUIRES_REWRITE_PARAMETER         = "_xRequiresRewrite";

  private PortletContext        mPortletContext;
  private PortletConfig         mPortletConfig;
  private PortletRequest        mPortletRequest;
  private PortletResponse       mPortletResponse;

  // Needed for distpach() which requires the actual PortletRequest/Response
  // objects not wrapped one's (since wrapping isn't official in 168)
  private PortletRequest        mOrigPortletRequest                = null;
  private PortletResponse       mOrigPortletResponse               = null;

  // External context maps
  private Map                   mApplicationMap                    = null;
  private Map                   mSessionMap                        = null;
  private Map                   mRequestMap                        = null;
  private Map                   mRequestParameterMap               = null;
  private Map                   mRequestParameterValuesMap         = null;
  private Map                   mRequestHeaderMap                  = null;
  private Map                   mRequestHeaderValuesMap            = null;
  private Map                   mInitParameterMap                  = null;

  // maps for internal parameters (eg, those specified in query string of
  // any defaultViewId)
  private Map                   mInternalRequestParameterMap       = Collections.EMPTY_MAP;
  private Map                   mInternalRequestParameterValuesMap = Collections.EMPTY_MAP;

  private PortletRequestHeaders mPortletRequestHeaders             = null;

  // Requested Faces view
  private String                mViewId                            = null;

  // Reverse engineered serlvet paths from mappings
  private Vector                mFacesMappings                     = null;
  private String                mServletPath                       = null;
  private String                mPathInfo                          = null;

  // Current Portlet phase
  private Bridge.PortletPhase   mPhase                             = null;

  public PortletExternalContextImpl(PortletConfig portletConfig, PortletRequest portletRequest,
                                    PortletResponse portletResponse) throws FacesException
  {
    mPortletConfig = portletConfig;
    mPortletContext = mPortletConfig.getPortletContext();
    mPortletRequest = mOrigPortletRequest = portletRequest;
    mPortletResponse = mOrigPortletResponse = portletResponse;

    mPhase = (Bridge.PortletPhase) mPortletRequest.getAttribute(Bridge.PORTLET_LIFECYCLE_PHASE);

    if (mPhase == Bridge.PortletPhase.RenderPhase)
    {
      log("PortletExternalContextImpl.<init>: RENDER");
      System.out.println("=====RENDER====="); // TODO DEBUG
    }
    else
    {
      log("PortletExternalContextImpl.<init>: ACTION");
      System.out.println("=====ACTION====="); // TODO DEBUG
    }

    mViewId = getViewId();

    // Now reverse engineer the servlet paths from the mappings
    mFacesMappings = (Vector) mPortletRequest.getAttribute(FACES_MAPPING_ATTRIBUTE);
    mapPathsFromViewId(mViewId, mFacesMappings);

    // Get the DelegateRender context parameter here and set as a request
    // attribute
    // so Bridge's ViewHandler has access to it. ViewHandler can't get from
    // context
    // itself because its a per portlet setting but without the config
    // object
    // the ViewHandler has no way to get the portlet's name.
    Bridge.BridgeRenderPolicy renderPolicy = (Bridge.BridgeRenderPolicy) mPortletContext
                                                                                        .getAttribute(Bridge.BRIDGE_PACKAGE_PREFIX
                                                                                                      + mPortletConfig
                                                                                                                      .getPortletName()
                                                                                                      + "."
                                                                                                      + Bridge.RENDER_POLICY);
    if (renderPolicy != null)
    {
      mPortletRequest.setAttribute(RENDER_POLICY_ATTRIBUTE, renderPolicy);
    }

  }

  public void release()
  {

    mPortletConfig = null;
    mPortletContext = null;
    mPortletRequest = null;
    mPortletResponse = null;
    mOrigPortletRequest = null;
    mOrigPortletResponse = null;

    mApplicationMap = null;
    mSessionMap = null;
    mRequestMap = null;
    mRequestParameterMap = null;
    mRequestParameterValuesMap = null;
    mRequestHeaderMap = null;
    mRequestHeaderValuesMap = null;
    mInitParameterMap = null;

    mViewId = null;
  }

  /**
   * This method is the gatekeeper for managing the viewId across action/render + subsequent
   * renders.
   * 
   * For the render case, when rendering the actionURL, we call this method to write the viewId in
   * the interaction state when calling createActionURL() This allows us to get the viewId in action
   * request. eg, /adf-faces-demo/componentDemos.jspx?_VIEW_ID=/componentDemos.jspx
   * 
   * For the action with redirect case, we call this method when the redirect() is called and we
   * encode the viewId in the navigational state so we can get the viewId in the subsequent render
   * request eg, /adf-faces-demo/componentDemos.jspx?_VIEW_ID=/componentDemos.jspx
   * 
   * We do the same as above for the action with non-redirect case as well by calling the redirect()
   * method at the end of action lifecycle in ADFBridgePorttlet.process() by passing in an URL
   * created by ViewHandler.getActionURL()
   * 
   * A special case to handle direct call from the goLink/goButton component in render request (bug
   * 5259313) eg, /components/goButton.jspx or http://www.oracle.com
   */
  @Override
  public String encodeActionURL(String url)
  {
    String viewId = null;
    QueryString queryStr = null;
    int queryStart = -1;

    if (url.startsWith("#") || isExternalURL(url) || isDirectLink(url))
    {
      return url;
    }

    // url might contain DirectLink=false parameter -- spec says remove if
    // it does.
    url = removeDirectLink(url);

    // Now determine the target viewId

    // First: if it looks this is a bridge encoded action url
    // try and process as such
    if (url.indexOf(VIEW_ID_QUERY_PARAMETER) > -1)
    {
      // First, parse and remove the viewId from the URL
      queryStart = url.indexOf('?');

      if (queryStart != -1)
      {
        // Get the query string
        queryStr = new QueryString(url.substring(queryStart + 1), "UTF8");

        // Now rebuild the URL without this parameter
        viewId = queryStr.getParameter(VIEW_ID_QUERY_PARAMETER);
        queryStr.removeParameter(VIEW_ID_QUERY_PARAMETER);

        String query = queryStr.toString();
        if (query != null && query.length() != 0)
        {
          url = url.substring(0, queryStart) + "?" + query;
        }
        else
        {
          url = url.substring(0, queryStart);
        }
      }
    }

    // Did we find the viewId? If not assume a native URL -- and hence
    // cull the viewId by using servlet path matching rules
    if (viewId == null)
    {
      if (!isRelativePath(url))
      {
        viewId = getViewIdFromPath(url);
      }
      else
      {
        viewId = getViewIdFromRelativePath(url);
      }
    }

    if (viewId == null)
    {
      throw new FacesException("encodeActionURL:  unable to recognize viewId");
    }

    if (mPhase == Bridge.PortletPhase.RenderPhase)
    { // render - write
      // the viewId into
      // the response
      // (interaction
      // state)
      RenderResponse renderResponse = (RenderResponse) getResponse();
      PortletURL actionURL = renderResponse.createActionURL();
      actionURL.setParameter(ACTION_ID_PARAMETER_NAME, viewId);

      // Add extra parameters so they don't get lost
      if (queryStr != null)
      {
        Enumeration list = queryStr.getParameterNames();
        while (list.hasMoreElements())
        {
          String param = list.nextElement().toString();
          actionURL.setParameter(param, queryStr.getParameter(param));
        }
      }

      // TODO hack to workaround double encoding problem
      String actionURLStr = actionURL.toString();
      actionURLStr = actionURLStr.replaceAll("\\&amp\\;", "&");
      log("PortletExternalContextImpl.encodeActionURL:end:RENDER(viewId=" + viewId
          + ";  actionURL=" + actionURLStr + ")");
      return actionURLStr;
    }
    else
    { // action - write the viewId to navigational state
      ActionResponse actionResponse = (ActionResponse) getResponse();

      actionResponse.setRenderParameter(ACTION_ID_PARAMETER_NAME, viewId);

      // set other request params (if any) into navigational states
      if (queryStr != null)
      {
        Enumeration list = queryStr.getParameterNames();
        while (list.hasMoreElements())
        {
          String param = list.nextElement().toString();
          actionResponse.setRenderParameter(param, queryStr.getParameter(param));
        }
      }

      log("PortletExternalContextImpl.encodeActionURL:end:ACTION(" + viewId + ")");

      // Since this is an action -- enocodeActionURL shouldn't be written
      // into markup anywhere -- hence nothing of value to return.
      // To distinguish between this case and when the path is absolute
      // (and
      // might be used for a redirect the spec says return something
      // different
      // then was passed in.

      // ContextPath is added so still seen as a local URL and hence isn't
      // processed by redirect
      return getRequestContextPath()
             + "/PortletFacesBridge/endcodeActionURL/encodeInActionPhase/doNothing";
    }
  }

  @Override
  public void redirect(String url) throws IOException
  {
    // Distinguish between redirects within this app and external links
    // redirects within this app are dealt (elsewhere) as navigations
    // so do nothing. External links are redirected

    log("PortletExternalContextImpl.redirect(" + url + ")");
    if (mPhase == Bridge.PortletPhase.ActionPhase
        && (url.startsWith("#") || isExternalURL(url) || isDirectLink(url)))
    {
      ((ActionResponse) getResponse()).sendRedirect(url);
    }

    // TODO: Should we recognize a redirect during a rendere to an internal
    // link and treat as a navigation?

  }

  @Override
  public String encodeResourceURL(String s)
  {

    if (!isExternalURL(s) && !s.startsWith("/"))
    {
      // must be a relative path -- convert it to contextPath relative
      // construct our cwd (servletPath + pathInfo);
      String pi = null;
      String path = getRequestServletPath();
      if (path == null)
      {
        path = getRequestPathInfo();
      }
      else
      {
        pi = getRequestPathInfo();
      }

      if (pi != null)
      {
        path = path.concat(pi);
      }

      // remove target
      path = path.substring(0, path.lastIndexOf("/"));
      s = URLUtils.convertFromRelative(path, s);
    }

    String resourceURLStr = mPortletResponse.encodeURL(s);

    // Avoid double encoding
    resourceURLStr = resourceURLStr.replaceAll("\\&amp\\;", "&");
    log("PortletExternalContextImpl.encodeResourceURL:end(" + resourceURLStr + ")");

    return resourceURLStr;
  }

  @Override
  public void dispatch(String requestURI) throws IOException, FacesException
  {
    if (requestURI == null)
    {
      throw new java.lang.NullPointerException();
    }

    if (mPhase == Bridge.PortletPhase.ActionPhase)
    {
      throw new IllegalStateException("Request cannot be an ActionRequest");
    }

    log("PortletExternalContextImpl.dispatch: response content type "
        + ((RenderResponse) mPortletResponse).getContentType());

    PortletRequestDispatcher prd = mPortletContext.getRequestDispatcher(requestURI);

    if (prd == null)
    {
      throw new IllegalArgumentException(
                                         "No request dispatcher can be created for the specified path: "
                                             + requestURI);
    }

    try
    {
      prd.include((RenderRequest) mOrigPortletRequest, (RenderResponse) mOrigPortletResponse);
    }
    catch (PortletException e)
    {
      if (e.getMessage() != null)
      {
        throw new FacesException(e.getMessage(), e);
      }
      else
      {
        throw new FacesException(e);
      }
    }
  }

  @Override
  public Object getSession(boolean create)
  {
    return mPortletRequest.getPortletSession(create);
  }

  @Override
  public Object getContext()
  {
    return mPortletContext;
  }

  @Override
  public Object getRequest()
  {
    return mPortletRequest;
  }

  @Override
  public Object getResponse()
  {
    return mPortletResponse;
  }

  @Override
  public Map getApplicationMap()
  {
    if (mApplicationMap == null)
    {
      log("PortletExternalContextImpl.getApplicationMap: creating PortletApplicationMap");
      mApplicationMap = new PortletApplicationMap(mPortletContext);
    }
    return mApplicationMap;
  }

  @Override
  public Map getSessionMap()
  {
    if (mSessionMap == null)
    {
      log("PortletExternalContextImpl.getSessionMap: creating PortletSessionMap");
      mSessionMap = new PortletSessionMap(mPortletRequest);
    }
    return mSessionMap;
  }

  @Override
  public Map getRequestMap()
  {
    if (mRequestMap == null)
    {
      log("PortletExternalContextImpl.getRequestMap: creating PortletRequestMap");
      mRequestMap = new PortletRequestMap(mPortletRequest);
    }
    return mRequestMap;
  }

  @Override
  public Map getRequestParameterMap()
  {
    if (mRequestParameterMap == null)
    {
      log("PortletExternalContextImpl.getRequestParameterMap: creating PortletRequestParameterMap");
      mRequestParameterMap = Collections
                                        .unmodifiableMap(new PortletRequestParameterMap(
                                                                                        mPortletRequest,
                                                                                        mInternalRequestParameterMap));
    }
    return mRequestParameterMap;
  }

  public Map getRequestParameterValuesMap()
  {
    if (mRequestParameterValuesMap == null)
    {
      log("PortletExternalContextImpl.getRequestParameterValuesMap: creating PortletRequestParameterValuesMap");
      mRequestParameterValuesMap = Collections
                                              .unmodifiableMap(new PortletRequestParameterValuesMap(
                                                                                                    mPortletRequest,
                                                                                                    mInternalRequestParameterValuesMap));
    }
    return mRequestParameterValuesMap;
  }

  public Iterator getRequestParameterNames()
  {
    Map requestParameters = getRequestParameterMap();
    final Iterator i = requestParameters.entrySet().iterator();
    Iterator it = new Iterator() {
      public boolean hasNext()
      {
        return i.hasNext();
      }

      public Object next()
      {
        Map.Entry entry = (Map.Entry) i.next();
        return entry.getKey();
      }

      public void remove()
      {
        throw new UnsupportedOperationException();
      }
    };

    return it;
  }

  public Map getRequestHeaderMap()
  {
    if (mRequestHeaderMap == null)
    {
      log("PortletExternalContextImpl.getRequestHeaderMap: creating PortletRequestHeaderMap");
      if (mPortletRequestHeaders == null)
      {
        mPortletRequestHeaders = new PortletRequestHeaders(mOrigPortletRequest);
      }

      mRequestHeaderMap = new PortletRequestHeaderMap(mPortletRequestHeaders);
    }
    return mRequestHeaderMap;
  }

  public Map getRequestHeaderValuesMap()
  {
    if (mRequestHeaderValuesMap == null)
    {
      log("PortletExternalContextImpl.getRequestHeaderValuesMap: creating PortletRequestHeaderValuesMap");
      if (mPortletRequestHeaders == null)
      {
        mPortletRequestHeaders = new PortletRequestHeaders(mOrigPortletRequest);
      }

      mRequestHeaderValuesMap = new PortletRequestHeaderValuesMap(mPortletRequestHeaders);
    }
    return mRequestHeaderValuesMap;
  }

  public Map getRequestCookieMap()
  {
    return Collections.unmodifiableMap(Collections.EMPTY_MAP);
  }

  public Locale getRequestLocale()
  {
    return mPortletRequest.getLocale();
  }

  public String getRequestPathInfo()
  {
    return mPathInfo;
  }

  public String getRequestContextPath()
  {
    return mPortletRequest.getContextPath();
  }

  public String getInitParameter(String s)
  {
    return mPortletContext.getInitParameter(s);
  }

  public Map getInitParameterMap()
  {
    if (mInitParameterMap == null)
    {
      log("PortletExternalContextImpl.getInitParameterMap: creating PortletInitParameterMap");
      mInitParameterMap = new PortletInitParameterMap(mPortletContext);
    }
    return mInitParameterMap;
  }

  public Set getResourcePaths(String s)
  {
    return mPortletContext.getResourcePaths(s);
  }

  public InputStream getResourceAsStream(String s)
  {
    return mPortletContext.getResourceAsStream(s);
  }

  public String encodeNamespace(String s)
  {
    if (!(mPortletResponse instanceof RenderResponse))
    {
      throw new IllegalStateException("Only RenderResponse can be used to encode a namespace");
    }
    else
    {
      return ((RenderResponse) mPortletResponse).getNamespace() + s;
    }
  }

  public String getRequestServletPath()
  {
    return mServletPath;
  }

  public String getAuthType()
  {
    return mPortletRequest.getAuthType();
  }

  public String getRemoteUser()
  {
    return mPortletRequest.getRemoteUser();
  }

  public boolean isUserInRole(String role)
  {
    return mPortletRequest.isUserInRole(role);
  }

  public Principal getUserPrincipal()
  {
    return mPortletRequest.getUserPrincipal();
  }

  public void log(String message)
  {
    mPortletContext.log(message);
  }

  public void log(String message, Throwable t)
  {
    mPortletContext.log(message, t);
  }

  public Iterator getRequestLocales()
  {
    return new LocalesIterator(mPortletRequest.getLocales());
  }

  public URL getResource(String s) throws MalformedURLException
  {
    return mPortletContext.getResource(s);
  }

  // Start of JSF 1.2 API

  /**
   * <p>
   * Set the environment-specific request to be returned by subsequent calls to {@link #getRequest}.
   * This may be used to install a wrapper for the request.
   * </p>
   * 
   * <p>
   * The default implementation throws <code>UnsupportedOperationException</code> and is provided
   * for the sole purpose of not breaking existing applications that extend this class.
   * </p>
   * 
   * 
   * @since 1.2
   */
  public void setRequest(Object request)
  {
    mPortletRequest = (PortletRequest) request;

    // clear out request based cached maps
    mRequestMap = null;
    mRequestParameterMap = null;
    mRequestParameterValuesMap = null;
    mRequestHeaderMap = null;
    mRequestHeaderValuesMap = null;
  }

  /**
   * 
   * <p>
   * Overrides the name of the character encoding used in the body of this request.
   * </p>
   * 
   * <p>
   * Calling this method after the request has been accessed will have no no effect, unless a
   * <code>Reader</code> or <code>Stream</code> has been obtained from the request, in which
   * case an <code>IllegalStateException</code> is thrown.
   * </p>
   * 
   * <p>
   * <em>Servlet:</em> This must call through to the <code>javax.servlet.ServletRequest</code>
   * method <code>setCharacterEncoding()</code>.
   * </p>
   * 
   * <p>
   * <em>Portlet:</em> This must call through to the <code>javax.portlet.ActionRequest</code>
   * method <code>setCharacterEncoding()</code>.
   * </p>
   * 
   * <p>
   * The default implementation throws <code>UnsupportedOperationException</code> and is provided
   * for the sole purpose of not breaking existing applications that extend this class.
   * </p>
   * 
   * @throws java.io.UnsupportedEncodingException
   *           if this is not a valid encoding
   * 
   * @since 1.2
   * 
   */
  public void setRequestCharacterEncoding(String encoding) throws UnsupportedEncodingException,
                                                          IllegalStateException
  {
    if (mPhase != Bridge.PortletPhase.ActionPhase)
    {
      throw new IllegalStateException(
                                      "PortletExternalContextImpl.setRequestCharacterEncoding(): Request must be an ActionRequest");
    }

    ((ActionRequest) mPortletRequest).setCharacterEncoding(encoding);
  }

  /**
   * 
   * <p>
   * Return the character encoding currently being used to interpret this request.
   * </p>
   * 
   * <p>
   * <em>Servlet:</em> This must return the value returned by the
   * <code>javax.servlet.ServletRequest</code> method <code>getCharacterEncoding()</code>.
   * </p>
   * 
   * <p>
   * <em>Portlet:</em> This must return the value returned by the
   * <code>javax.portlet.ActionRequest</code> method <code>getCharacterEncoding()</code>.
   * </p>
   * 
   * <p>
   * The default implementation throws <code>UnsupportedOperationException</code> and is provided
   * for the sole purpose of not breaking existing applications that extend this class.
   * </p>
   * 
   * @since 1.2
   * 
   */
  public String getRequestCharacterEncoding()
  {
    if (mPhase == Bridge.PortletPhase.RenderPhase)
    {
      throw new IllegalStateException(
                                      "PortletExternalContextImpl.getRequestCharacterEncoding(): Request must be an ActionRequest");
    }

    return ((ActionRequest) mPortletRequest).getCharacterEncoding();
  }

  /**
   * 
   * <p>
   * Return the MIME Content-Type for this request. If not available, return <code>null</code>.
   * </p>
   * 
   * <p>
   * <em>Servlet:</em> This must return the value returned by the
   * <code>javax.servlet.ServletRequest</code> method <code>getContentType()</code>.
   * </p>
   * 
   * <p>
   * <em>Portlet:</em> This must return <code>null</code>.
   * </p>
   * 
   * NOTE: We are deviating from the javadoc based on recommendation from JSR 301 expert group
   * 
   * <p>
   * The default implementation throws <code>UnsupportedOperationException</code> and is provided
   * for the sole purpose of not breaking existing applications that extend this class.
   * </p>
   * 
   * @since 1.2
   */
  public String getRequestContentType()
  {
    if (mPhase == Bridge.PortletPhase.RenderPhase)
    {
      throw new IllegalStateException(
                                      "PortletExternalContextImpl.getRequestContentType(): Request must be an ActionRequest");
    }

    return ((ActionRequest) mPortletRequest).getContentType();
  }

  /**
   * 
   * <p>
   * Returns the name of the character encoding (MIME charset) used for the body sent in this
   * response.
   * </p>
   * 
   * <p>
   * <em>Servlet:</em> This must return the value returned by the
   * <code>javax.servlet.ServletResponse</code> method <code>getCharacterEncoding()</code>.
   * </p>
   * 
   * <p>
   * <em>Portlet:</em> This must return <code>null</code>.
   * </p>
   * 
   * NOTE: We are deviating from the javadoc based on recommendation from JSR 301 expert group
   * 
   * <p>
   * The default implementation throws <code>UnsupportedOperationException</code> and is provided
   * for the sole purpose of not breaking existing applications that extend this class.
   * </p>
   * 
   * @since 1.2
   */
  public String getResponseCharacterEncoding()
  {
    if (mPhase == Bridge.PortletPhase.ActionPhase)
    {
      throw new IllegalStateException(
                                      "PortletExternalContextImpl.getResponseCharacterEncoding(): Response must be a RenderRequest");
    }

    return ((RenderResponse) mPortletResponse).getCharacterEncoding();
  }

  /**
   * 
   * <p>
   * Return the MIME Content-Type for this response. If not available, return <code>null</code>.
   * </p>
   * 
   * <p>
   * <em>Servlet:</em> This must return the value returned by the
   * <code>javax.servlet.ServletResponse</code> method <code>getContentType()</code>.
   * </p>
   * 
   * <p>
   * <em>Portlet:</em> This must return <code>null</code>.
   * </p>
   * 
   * NOTE: We are deviating from the javadoc based on recommendation from JSR 301 expert group
   * 
   * <p>
   * The default implementation throws <code>UnsupportedOperationException</code> and is provided
   * for the sole purpose of not breaking existing applications that extend this class.
   * </p>
   * 
   * @since 1.2
   */
  public String getResponseContentType()
  {
    if (mPhase == Bridge.PortletPhase.ActionPhase)
    {
      throw new IllegalStateException(
                                      "PortletExternalContextImpl.getResponseContentType(): Response must be a RenderRequest");
    }

    return ((RenderResponse) mPortletResponse).getContentType();
  }

  /**
   * <p>
   * Set the environment-specific response to be returned by subsequent calls to
   * {@link #getResponse}. This may be used to install a wrapper for the response.
   * </p>
   * 
   * <p>
   * The default implementation throws <code>UnsupportedOperationException</code> and is provided
   * for the sole purpose of not breaking existing applications that extend this class.
   * </p>
   * 
   * 
   * @since 1.2
   */
  public void setResponse(Object response)
  {
    mPortletResponse = (PortletResponse) response;
  }

  /**
   * 
   * <p>
   * Sets the character encoding (MIME charset) of the response being sent to the client, for
   * example, to UTF-8.
   * </p>
   * 
   * <p>
   * <em>Servlet:</em> This must call through to the <code>javax.servlet.ServletResponse</code>
   * method <code>setCharacterEncoding()</code>.
   * </p>
   * 
   * <p>
   * <em>Portlet:</em> This method must take no action.
   * </p>
   * 
   * <p>
   * The default implementation throws <code>UnsupportedOperationException</code> and is provided
   * for the sole purpose of not breaking existing applications that extend this class.
   * </p>
   * 
   * 
   * @since 1.2
   * 
   */
  public void setResponseCharacterEncoding(String encoding)
  {
    // JSR 168 has no corresponding API.
  }

  // End of JSF 1.2 API

  /**
   * Gets the view identifier we should use for this request.
   */
  private String getViewId() throws BridgeDefaultViewNotSpecifiedException
  {
    String viewId = (String) mPortletRequest.getParameter(ACTION_ID_PARAMETER_NAME);

    log("PortletExternalContextImpl.getViewId: found action_id = " + viewId);

    // If no defaultview then throw an exception
    if (viewId == null)
    {
      viewId = (String) mPortletRequest.getAttribute(Bridge.DEFAULT_VIEWID);
      if (viewId == null)
      {
        throw new BridgeDefaultViewNotSpecifiedException();
      }
      log("PortletExternalContextImpl.getViewId: action_id not found, defaulting to: " + viewId);
    }

    // Some viewId may have query string, so handle that here
    // (e.g., TaskFlow has the following viewId:
    // /adf.task-flow?_document=/WEB-INF/task-flow.xml&_id=task1

    int queryStart = viewId.indexOf('?');
    QueryString queryStr = null;

    if (queryStart != -1)
    {
      // parse the query string and add the parameters to internal maps
      // delay the creation of ParameterMap and ParameterValuesMap until
      // they are needed/called by the client
      queryStr = new QueryString(viewId.substring(queryStart + 1), "UTF8");

      mInternalRequestParameterMap = new HashMap(5);
      mInternalRequestParameterValuesMap = new HashMap(5);

      Enumeration list = queryStr.getParameterNames();
      while (list.hasMoreElements())
      {
        String param = (String) list.nextElement().toString();
        mInternalRequestParameterMap.put(param, queryStr.getParameter(param));
        mInternalRequestParameterValuesMap
                                          .put(param, new String[] { queryStr.getParameter(param) });
      }

      viewId = viewId.substring(0, queryStart);
      log("PortletExternalContextImpl.getViewId: special viewId: " + viewId);
    }

    return viewId;
  }

  private void mapPathsFromViewId(String viewId, Vector mappings)
  {
    if (viewId == null || mappings == null)
    {
      // Fail safe -- even if we didn't find a servlet mapping set path
      // info
      // as if we did as this value is all anything generally depends on
      mPathInfo = viewId;
      return;
    }

    // see if the viewId terminates with an extension
    // if non-null value contains *.XXX where XXX is the extension
    String ext = extensionMappingFromViewId(viewId);
    if (ext != null && mappings.contains(ext))
    {
      // we are extension mapped
      mServletPath = viewId;
      mPathInfo = null;
      return;
    }

    // Otherwise we must be servlet mapped. Use the first path defined
    // in mappings that isn't extension mapped
    for (int i = 0; i < mappings.size(); i++)
    {
      String mapping = (String) mappings.elementAt(i);
      if (mapping.startsWith("/"))
      {
        int j = mapping.lastIndexOf("/*");
        if (j != -1)
        {
          mServletPath = mapping.substring(0, j);
        }
      }
    }
    // Fail safe -- even if we didn't find a servlet mapping set path info
    // as if we did as this value is all anything generally depends on
    mPathInfo = viewId;
  }

  private String extensionMappingFromViewId(String viewId)
  {
    int extLoc = viewId.lastIndexOf('.');

    if (extLoc != -1 && extLoc > viewId.lastIndexOf('/'))
    {
      StringBuffer sb = new StringBuffer("*");
      sb.append(viewId.substring(extLoc));
      return sb.toString();
    }
    return null;
  }

  private String getViewIdFromPath(String url)
  {
    // Get a string that holds the path after the Context-Path through the
    // target

    // First remove the query string
    int i = url.indexOf("?");
    if (i != -1)
    {
      url = url.substring(0, i);
    }

    // Now remove up through the ContextPath
    String ctxPath = getRequestContextPath();
    i = url.indexOf(ctxPath);
    if (i != -1)
    {
      url = url.substring(i + ctxPath.length());
    }

    String viewId = null;
    // Okay now figure out whether this is prefix or suffixed mapped
    if (isSuffixedMapped(url, mFacesMappings))
    {
      viewId = viewIdFromSuffixMapping(
                                       url,
                                       mFacesMappings,
                                       mPortletContext
                                                      .getInitParameter(ViewHandler.DEFAULT_SUFFIX_PARAM_NAME));
    }
    if (isPrefixedMapped(url, mFacesMappings))
    {
      viewId = viewIdFromPrefixMapping(url, mFacesMappings);
    }
    else
    {
      // Set to what follows the URL
      viewId = url;
    }
    return viewId;
  }

  private boolean isSuffixedMapped(String url, Vector mappings)
  {
    // see if the viewId terminates with an extension
    // if non-null value contains *.XXX where XXX is the extension
    String ext = extensionMappingFromViewId(url);
    return ext != null && mappings.contains(ext);
  }

  private String viewIdFromSuffixMapping(String url, Vector mappings, String ctxDefault)
  {
    // replace extension with the DEFAULT_SUFFIX
    if (ctxDefault == null)
    {
      ctxDefault = ViewHandler.DEFAULT_SUFFIX;
    }

    int i = url.lastIndexOf(".");
    if (ctxDefault != null && i != -1)
    {
      if (ctxDefault.startsWith("."))
      {
        url = url.substring(0, i) + ctxDefault;
      }
      else
      {
        // shouldn't happen
        url = url.substring(0, i) + "." + ctxDefault;
      }
    }
    return url;
  }

  private boolean isPrefixedMapped(String url, Vector mappings)
  {
    for (int i = 0; i < mappings.size(); i++)
    {
      String prefix = null;
      String mapping = (String) mappings.elementAt(i);
      if (mapping.startsWith("/"))
      {
        int j = mapping.lastIndexOf("/*");
        if (j != -1)
        {
          prefix = mapping.substring(0, j);
        }
      }
      if (prefix != null && url.startsWith(prefix))
      {
        return true;
      }
    }
    return false;
  }

  private String viewIdFromPrefixMapping(String url, Vector mappings)
  {
    for (int i = 0; i < mappings.size(); i++)
    {
      String prefix = null;
      String mapping = (String) mappings.elementAt(i);
      if (mapping.startsWith("/"))
      {
        int j = mapping.lastIndexOf("/*");
        if (j != -1)
        {
          prefix = mapping.substring(0, j);
        }
      }
      if (prefix != null && url.startsWith(prefix))
      {
        return url.substring(prefix.length());
      }
    }
    return null;
  }

  private String viewIdWithPrefixMapping(String viewId, Vector mappings)
  {
    // use first prefix mapping found
    for (int i = 0; i < mappings.size(); i++)
    {
      String prefix = null;
      String mapping = (String) mappings.elementAt(i);
      if (mapping.startsWith("/"))
      {
        int j = mapping.lastIndexOf("/*");
        if (j != -1)
        {
          prefix = mapping.substring(0, j);
        }
      }
      if (prefix != null)
      {
        if (viewId.startsWith("/"))
        {
          return prefix + viewId;
        }
        else
        {
          return prefix + "/" + viewId;
        }
      }
    }

    return null;
  }

  private boolean isAbsoluteURL(String url)
  {
    if (url.startsWith("http"))
    {
      return true;
    }

    // now deal with other possible protocols
    int i = url.indexOf(":");
    if (i == -1)
    {
      return false;
    }
    int j = url.indexOf("/");
    if (j != -1 && j > i)
    {
      return true;
    }
    return false;
  }

  private boolean isExternalURL(String url)
  {

    if (!isAbsoluteURL(url))
    {
      return false;
    }

    // otherwise see if the URL contains the ContextPath

    // Simple test is that the url doesn't contain
    // the CONTEXT_PATH -- though ultimately may want to test
    // if we are on the same server
    String ctxPath = getRequestContextPath();
    int i = url.indexOf(ctxPath);
    int j = url.indexOf("?");
    if (i != -1 && (j == -1 || i < j))
    {
      return false;
    }
    return true;
  }

  private boolean isDirectLink(String url)
  {
    int queryStart = url.indexOf('?');
    QueryString queryStr = null;
    String directLink = null;

    if (queryStart != -1)
    {
      queryStr = new QueryString(url.substring(queryStart + 1), "UTF8");
      directLink = queryStr.getParameter(Bridge.DIRECT_LINK);
      return Boolean.parseBoolean(directLink);
    }

    return false;
  }

  private String removeDirectLink(String url)
  {
    int queryStart = url.indexOf('?');
    QueryString queryStr = null;
    String directLink = null;

    if (queryStart != -1)
    {
      queryStr = new QueryString(url.substring(queryStart + 1), "UTF8");
      directLink = queryStr.getParameter(Bridge.DIRECT_LINK);
      if (!Boolean.parseBoolean(directLink))
      {
        queryStr.removeParameter(Bridge.DIRECT_LINK);
        String query = queryStr.toString();
        if (query != null && query.length() != 0)
        {
          url = url.substring(0, queryStart + 1) + query;
        }
      }
    }

    return url;
  }

  private String getViewIdFromRelativePath(String url)
  {
    String currentViewId = getViewId();
    int i = currentViewId.indexOf('?');
    if (i != -1)
    {
      currentViewId = currentViewId.substring(0, i);
    }

    String prefixURL = currentViewId.substring(0, currentViewId.lastIndexOf('/'));

    if (prefixURL.length() != 0 && !prefixURL.startsWith("/"))
    {
      log("PortletExternalContextImpl.encodeActionURL:end:in(" + url + ")");
      return null; // this shouldn't happen, if so just return
    }

    if (url.startsWith("./"))
    {
      url = url.substring(2);
    }
    while (url.startsWith("../") && prefixURL.length() != 0)
    {
      url = url.substring(3);
      prefixURL = prefixURL.substring(0, prefixURL.lastIndexOf('/'));
    }
    url = prefixURL + "/" + url;

    // Now check to see if suffix mapped because we need to do the extension
    // mapping
    if (isSuffixedMapped(url, mFacesMappings))
    {
      url = viewIdFromSuffixMapping(
                                    url,
                                    mFacesMappings,
                                    mPortletContext
                                                   .getInitParameter(ViewHandler.DEFAULT_SUFFIX_PARAM_NAME));
    }

    return url;
  }

  private boolean isRelativePath(String url)
  {
    // relative path doesn't start with a '/'
    if (url.startsWith("/"))
    {
      return false;
    }

    // relative path if starts with a '.' or doesn't contain a '/'
    if (url.startsWith("."))
    {
      return true;
    }

    // neither obviously a relative path or not -- now discount protocol
    int i = url.indexOf("://");
    if (i == -1)
    {
      return true;
    }

    // make sure : isn't in querystring
    int j = url.indexOf('?');
    if (j != -1 && j < i)
    {
      return true;
    }

    return false;
  }

}
