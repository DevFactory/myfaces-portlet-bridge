/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable
 * law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 */
package javax.portlet.faces;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import java.util.ArrayList;
import java.util.List;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.GenericPortlet;
import javax.portlet.PortletConfig;
import javax.portlet.PortletContext;
import javax.portlet.PortletException;
import javax.portlet.PortletMode;
import javax.portlet.PortletRequest;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.WindowState;

/**
 * The <code>GenericFacesPortlet</code> is provided to simplify development of a portlet that in
 * whole or part relies on the Faces bridge to process requests. If all requests are to be handled
 * by the bridge, <code>GenericFacesPortlet</code> is a turnkey implementation. Developers do not
 * need to subclass it. However, if there are some situations where the portlet doesn't require
 * bridge services then <code>GenericFacesPortlet</code> can be subclassed and overriden.
 * <p>
 * Since <code>GenericFacesPortlet</code> subclasses <code>GenericPortlet</code> care is taken
 * to all subclasses to override naturally. For example, though <code>doDispatch()</code> is
 * overriden, requests are only dispatched to the bridge from here if the <code>PortletMode</code>
 * isn't <code>VIEW</code>, <code>EDIT</code>, or <code>HELP</code>.
 * <p>
 * The <code>GenericFacesPortlet</code> recognizes the following portlet init parameters:
 * <ul>
 * <li><code>javax.portlet.faces.defaultViewId.[<i>mode</i>]</code>: specifies on a per mode
 * basis the default viewId the Bridge executes when not already encoded in the incoming request. A
 * value must be defined for each <code>PortletMode</code> the <code>Bridge</code> is expected
 * to process. </li>
 * </ul>
 * The <code>GenericFacesPortlet</code> recognizes the following <code>
 * PortletContext</code>
 * init parameters:
 * <ul>
 * <li><code>javax.portlet.faces.BridgeImplClass</code>: specifies the <code>Bridge</code>implementation
 * class used by this portlet. This init parameter must be specified or else an exception is thrown.
 * </li>
 * </ul>
 */
public class GenericFacesPortlet extends GenericPortlet
{
  public static final String BRIDGE_CLASS             = Bridge.BRIDGE_PACKAGE_PREFIX
                                                        + "BridgeImplClass";
  public static final String BRIDGE_SERVICE_CLASSPATH = "META-INF/services/javax.portlet.faces.Bridge";

  private Class<? extends Bridge> mFacesBridgeClass   = null;
  private Bridge                  mFacesBridge        = null;

  /**
   * Initialize generic faces portlet from portlet.xml
   */
  @SuppressWarnings("unchecked")
  @Override
  public void init(PortletConfig portletConfig) throws PortletException
  {
    super.init(portletConfig);

    // Make sure the bridge impl class is defined -- if not then search for it
    // using same search rules as Faces
    String bridgeClassName = getBridgeClassName();

    if (bridgeClassName != null)
    {
      try
      {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        mFacesBridgeClass = (Class<? extends Bridge>)loader.loadClass(bridgeClassName);
      }
      catch (ClassNotFoundException cnfe)
      {
        // Do nothing and fall through to null check
      }
    }

    if (mFacesBridgeClass == null)
    {
      throw new PortletException("Configuration Error: Initial Parameter '" + BRIDGE_CLASS
                                 + "' is not defined for portlet: " + getPortletName());
    }
    
    // Get the other bridge configuration parameters and set as context attributes
    List<String> excludedAttrs = getExcludedRequestAttributes();
    if (excludedAttrs != null)
    {
      getPortletContext().setAttribute(
                                       Bridge.BRIDGE_PACKAGE_PREFIX + getPortletName() + "."
                                           + Bridge.EXCLUDED_REQUEST_ATTRIBUTES,
                                       excludedAttrs);
    }
    
    Boolean preserveActionParams = getPreserveActionParameters();
    getPortletContext().setAttribute(
                                      Bridge.BRIDGE_PACKAGE_PREFIX + getPortletName() + "."
                                      + Bridge.PRESERVE_ACTION_PARAMS,
                                      preserveActionParams);

    // Don't instanciate/initialize the bridge yet. Do it on first use
  }

  /**
   * Release resources
   */
  @Override
  public void destroy()
  {
    if (mFacesBridge != null)
    {
      mFacesBridge.destroy();
      mFacesBridge = null;
      mFacesBridgeClass = null;
    }
  }

  /**
   * If mode is VIEW, EDIT, or HELP -- defer to the doView, doEdit, doHelp so subclasses can
   * override. Otherwise handle mode here if there is a defaultViewId mapping for it.
   */
  @Override
  public void doDispatch(RenderRequest request, RenderResponse response) throws PortletException,
                                                                        IOException
  {
    // Defer to helper methods for standard modes so subclasses can override
    PortletMode mode = request.getPortletMode();
    if (mode == PortletMode.EDIT || mode == PortletMode.HELP || mode == PortletMode.VIEW)
    {
      super.doDispatch(request, response);
    }
    else
    {
      // Bridge didn't process this one -- so forge ahead
      if (!doDispatchInternal(request, response))
      {
        super.doDispatch(request, response);
      }
    }
  }

  @Override
  protected void doEdit(RenderRequest request, RenderResponse response) throws PortletException,
                                                                       java.io.IOException
  {
    doDispatchInternal(request, response);

  }

  @Override
  protected void doHelp(RenderRequest request, RenderResponse response) throws PortletException,
                                                                       java.io.IOException
  {
    doDispatchInternal(request, response);

  }

  @Override
  protected void doView(RenderRequest request, RenderResponse response) throws PortletException,
                                                                       java.io.IOException
  {
    doDispatchInternal(request, response);

  }

  @Override
  public void processAction(ActionRequest request, ActionResponse response)
                                                                           throws PortletException,
                                                                           IOException
  {
    doBridgeDispatch(request, response, getDefaultViewId(request, request.getPortletMode()));
  }

  /**
   * Returns the set of RequestAttribute names that the portlet wants the bridge to
   * exclude from its managed request scope.  This default implementation picks up
   * this list from the comma delimited init_param javax.portlet.faces.excludedRequestAttributes.
   * 
   * @return a List containing the names of the attributes to be excluded. null if it can't be
   *         determined.
   */
  public List<String> getExcludedRequestAttributes()
  {
    String excludedAttrs = getPortletConfig()
                              .getInitParameter(
                                Bridge.BRIDGE_PACKAGE_PREFIX
                                + Bridge.EXCLUDED_REQUEST_ATTRIBUTES);
    if (excludedAttrs == null)  
    {
      return null;
    }
    
    String[] attrArray = excludedAttrs.split(",");
    // process comma delimited String into a List
    ArrayList<String> list = new ArrayList(attrArray.length);
    for (int i = 0; i < attrArray.length; i++)
    {
      list.add(attrArray[i]);
    }
    return list;
  }

  /**
   * Returns a boolean indicating whether or not the bridge should preserve all the
   * action parameters in the subsequent renders that occur in the same scope.  This
   * default implementation reads the values from the portlet init_param
   * javax.portlet.faces.preserveActionParams.  If not present, false is returned.
   * 
   * @return a boolean indicating whether or not the bridge should preserve all the
   * action parameters in the subsequent renders that occur in the same scope.
   */
  public Boolean getPreserveActionParameters()
  {
    String preserveActionParams = getPortletConfig()
                                    .getInitParameter(
                                      Bridge.BRIDGE_PACKAGE_PREFIX);
    if (preserveActionParams == null)
    {
      return Boolean.FALSE;
    }
    else
    {
      return Boolean.valueOf(preserveActionParams);
    }
  }

  /**
   * Returns the className of the bridge implementation this portlet uses. Subclasses override to
   * alter the default behavior. Default implementation first checks for a portlet context init
   * parameter: javax.portlet.faces.BridgeImplClass. If it doesn't exist then it looks for the
   * resource file "/META-INF/services/javax.portlet.faces.Bridge" using the current threads
   * classloader and extracts the classname from the first line in that file.
   * 
   * @return the class name of the Bridge class the GenericFacesPortlet uses. null if it can't be
   *         determined.
   */
  public String getBridgeClassName()
  {
    String bridgeClassName = getPortletConfig().getPortletContext().getInitParameter(BRIDGE_CLASS);

    if (bridgeClassName == null)
    {
      bridgeClassName = getFromServicesPath(getPortletConfig().getPortletContext(),
                                            BRIDGE_SERVICE_CLASSPATH);
    }
    return bridgeClassName;
  }

  /**
   * Returns the defaultViewId to be used for this request. The defaultViewId is depends on the
   * PortletMode.
   * 
   * @param request
   *          the request object.
   * @param mode
   *          the mode which to return the defaultViewId for.
   * @return the defaultViewId for this mode
   */
  public String getDefaultViewId(PortletRequest request, PortletMode mode)
  {
    return getPortletConfig().getInitParameter(Bridge.DEFAULT_VIEWID + "." + mode.toString());
  }

  private boolean doDispatchInternal(RenderRequest request, RenderResponse response)
     throws PortletException, IOException
  {
    String modeDefaultViewId = getDefaultViewId(request, request.getPortletMode());
    
    if (modeDefaultViewId != null)
    {
      WindowState state = request.getWindowState();
      if (!state.equals(WindowState.MINIMIZED))
      {
        doBridgeDispatch(request, response, modeDefaultViewId);
      }
      return true;
    }
    else
    {
      return false;
    }
  }

  private void doBridgeDispatch(RenderRequest request, RenderResponse response, String defaultViewId)
                                                                                                     throws PortletException
  {
    // initial Bridge if not already active
    initBridge();
    // Push information for Bridge into request attributes
    setBridgeRequestContext(request, defaultViewId);
    try
    {
      mFacesBridge.doFacesRequest(request, response);
    }
    catch (BridgeException e)
    {
      throw new PortletException(
                                 "doBridgeDispatch failed:  error from Bridge in executing the request",
                                 e);
    }

  }

  private void doBridgeDispatch(ActionRequest request, ActionResponse response, String defaultViewId)
                                                                                                     throws PortletException
  {
    // initial Bridge if not already active
    initBridge();
    // Push information for Bridge into request attributes
    setBridgeRequestContext(request, defaultViewId);
    try
    {
      mFacesBridge.doFacesRequest(request, response);
    }
    catch (BridgeException e)
    {
      throw new PortletException(
                                 "doBridgeDispatch failed:  error from Bridge in executing the request",
                                 e);
    }

  }

  private void initBridge() throws PortletException
  {
    if (mFacesBridge == null)
    {
      try
      {
        mFacesBridge = mFacesBridgeClass.newInstance();
        mFacesBridge.init(getPortletConfig());
      }
      catch (Exception e)
      {
        throw new PortletException("doBridgeDisptach:  error instantiating the bridge class", e);
      }
    }
  }

  private void setBridgeRequestContext(PortletRequest request, String defaultViewId)
  {
    // Make the defaultViewId available to the Bridge
    request.setAttribute(Bridge.DEFAULT_VIEWID, defaultViewId);
  }

  private String getFromServicesPath(PortletContext context, String resourceName)
  {
    // Check for a services definition
    String result = null;
    BufferedReader reader = null;
    InputStream stream = null;
    try
    {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      if (cl == null)
      {
        return null;
      }

      stream = cl.getResourceAsStream(resourceName);
      if (stream != null)
      {
        // Deal with systems whose native encoding is possibly
        // different from the way that the services entry was created
        try
        {
          reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        }
        catch (UnsupportedEncodingException e)
        {
          reader = new BufferedReader(new InputStreamReader(stream));
        }
        result = reader.readLine();
        if (result != null)
        {
          result = result.trim();
        }
        reader.close();
        reader = null;
        stream = null;
      }
    }
    catch (IOException e)
    {
    }
    catch (SecurityException e)
    {
    }
    finally
    {
      if (reader != null)
      {
        try
        {
          reader.close();
          stream = null;
        }
        catch (Throwable t)
        {
          ;
        }
        reader = null;
      }
      if (stream != null)
      {
        try
        {
          stream.close();
        }
        catch (Throwable t)
        {
          ;
        }
        stream = null;
      }
    }
    return result;
  }

}
