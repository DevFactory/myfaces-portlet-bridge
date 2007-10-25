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

package org.apache.myfaces.portlet.faces.bridge;

import java.io.IOException;
import java.io.Serializable;

import java.rmi.server.UID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.el.ELContext;
import javax.el.ELContextEvent;
import javax.el.ELContextListener;

import javax.faces.FacesException;
import javax.faces.FactoryFinder;
import javax.faces.application.Application;
import javax.faces.application.ApplicationFactory;
import javax.faces.application.FacesMessage;
import javax.faces.application.ViewHandler;
import javax.faces.component.UIViewRoot;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;

import javax.faces.context.FacesContextFactory;
import javax.faces.event.PhaseEvent;
import javax.faces.event.PhaseId;
import javax.faces.event.PhaseListener;
import javax.faces.lifecycle.Lifecycle;
import javax.faces.lifecycle.LifecycleFactory;
import javax.faces.render.ResponseStateManager;
import javax.faces.webapp.FacesServlet;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortalContext;
import javax.portlet.PortletConfig;
import javax.portlet.PortletContext;
import javax.portlet.PortletPreferences;
import javax.portlet.PortletRequest;
import javax.portlet.PortletResponse;
import javax.portlet.PortletSession;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import javax.portlet.faces.Bridge;
import javax.portlet.faces.BridgeException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;

import org.apache.myfaces.portlet.faces.bridge.wrapper.BridgeRenderRequestWrapper;
import org.apache.myfaces.portlet.faces.util.config.WebConfigurationProcessor;
import org.apache.myfaces.portlet.faces.context.PortletExternalContextImpl;

public class BridgeImpl implements Bridge, ELContextListener, PhaseListener
{

  /**
   * 
   */
  private static final long    serialVersionUID                = -2181720908326762776L;
  private static final String  REQUEST_SCOPE_LOCK              = "org.apache.myfaces.portlet.faces.requestScopeLock";
  private static final String  REQUEST_SCOPE_MAP               = "org.apache.myfaces.portlet.faces.requestScopeMap";
  private static final String  REQUEST_SCOPE_LISTENER          = "org.apache.myfaces.portlet.faces.requestScopeWatch";
  private static final String  FACES_VIEWROOT                  = "org.apache.myfaces.portlet.faces.facesViewRoot";
  private static final String  FACES_MESSAGES                  = "org.apache.myfaces.portlet.faces.facesMessages";
  private static final String  REQUEST_PARAMETERS              = "org.apache.myfaces.portlet.faces.requestParameters";
  private static final String  REQUEST_SCOPE_ID_RENDER_PARAM   = "_bridgeRequestScopeId";

  private PortletConfig        mPortletConfig                  = null;
  private boolean              mPreserveActionParams           = false;

  private FacesContextFactory  mFacesContextFactory            = null;
  private Lifecycle            mLifecycle                      = null;

  private Vector               mFacesMappings                  = null;

  private static final Integer sDefaultMaxManagedRequestScopes = new Integer(100);

  public BridgeImpl()
  {
    // everything gets done in the init call.
  }

  public void init(PortletConfig config) throws BridgeException
  {
    mPortletConfig = config;
    PortletContext portletContext = mPortletConfig.getPortletContext();

    // get preserveActionParams here because we use later in this class.
    // however don't process renderPolicy here because its used in
    // ViewHandler (and needs to be at request scope) -- hence this
    // is done in ExternalContext.
    Boolean configParam = (Boolean) portletContext.getAttribute(Bridge.BRIDGE_PACKAGE_PREFIX
                                                                + mPortletConfig.getPortletName()
                                                                + "."
                                                                + Bridge.PRESERVE_ACTION_PARAMS);
    if (configParam != null)
    {
      mPreserveActionParams = configParam.booleanValue();
    }

    // Set up the synchronziation object for the RequestScopeMap as we don't
    // want to sync on the PortletContext because its too broad. Note:
    // needed
    // because we not only need to sync the Map but also creating the Map
    // and
    // putting it in the PortletContext. Hence the sync object allows us
    // to limit syncronizing the PortletContext to once per portlet (init
    // time);
    synchronized (portletContext)
    {
      Object lock = portletContext.getAttribute(REQUEST_SCOPE_LOCK);
      if (lock == null)
      {
        portletContext.setAttribute(REQUEST_SCOPE_LOCK, new Object());
      }
    }

    // Add self as ELContextListener to the Faces App so we can add the
    // portletConfig to any newly created contexts.
    ApplicationFactory appFactory = (ApplicationFactory) FactoryFinder
                                                                      .getFactory(FactoryFinder.APPLICATION_FACTORY);
    Application app = appFactory.getApplication();
    app.addELContextListener(this);

    // Process and cache the FacesServlet mappings for use by
    // ExternalContext
    WebConfigurationProcessor facesConfig = new WebConfigurationProcessor(portletContext);
    mFacesMappings = facesConfig.getFacesMappings();
    for (int i = 0; i < mFacesMappings.size(); i++)
    {
      portletContext.log("Mapping: " + (String) mFacesMappings.elementAt(i));
    }
  }

  public void doFacesRequest(ActionRequest request, ActionResponse response) throws BridgeException
  {
    Map m = null;
    // Set the Portlet lifecycle phase as a request attribute so its
    // available to Faces extensions -- allowing that code to NOT rely on
    // instanceof which can fail if a portlet container uses a single class
    // to implement both the action and render request/response objects
    request.setAttribute(Bridge.PORTLET_LIFECYCLE_PHASE, Bridge.PortletPhase.ActionPhase);

    // Set the FacesServletMapping attribute so the ExternalContext can
    // pick it up and use it to reverse map viewIds to paths
    if (mFacesMappings != null)
    {
      request.setAttribute(PortletExternalContextImpl.FACES_MAPPING_ATTRIBUTE, mFacesMappings);
    }

    // cache names of existing request attributes so can exclude them
    // from being saved in the bridge's request scope. Note: this is done
    // before
    // acquiring the FacesContext because its possible (though unlikely)
    // the application has inserted itself in this process and sets up
    // needed request attributes.
    String[] excludedAttributes = getExcludedAttributes(request);

    FacesContext context = null;
    try
    {
      // Get the FacesContext instance for this request
      context = getFacesContextFactory().getFacesContext(mPortletConfig, request, response,
                                                         getLifecycle());
      logMap("Received ActionParams: ", context.getExternalContext().getRequestParameterValuesMap());

      // Each action starts a new "action lifecycle"
      // The Bridge preserves request scoped data and if so configured
      // Action Parameters for the duration of an action lifecycle
      String scopeId = initBridgeRequestScope(response);

      // For actions we only execute the lifecycle phase
      getLifecycle().execute(context);

      // Check responseComplete -- if responseComplete the
      // lifecycle.execute
      // resulted in a redirect navigation. To preserve Faces semantics
      // the viewState isn't preserved nor is the data associated with the
      // action lifecycle.

      if (!context.getResponseComplete())
      {
        // navigation didn't redirect

        // Before preserving the request scope data in the bridge's
        // request scope,
        // put the Faces view into request scope. This is done because
        // JSF 1.2 manages the tree save state opaquely exclusively in
        // the render phase -- I.e. there is no JSF 1.2 way of having
        // the
        // bridge manually save and restore the view
        saveFacesView(context);

        // Spec requires we preserve the FACES_VIEW_STATE parameter
        // in addition the portlet may be configured to preserve the
        // rest of them.
        saveActionParams(context);

        // Because the portlet model doesn't execute its render phase
        // within the same request scope but Faces does (assumes this),
        // preserve the request scope data and the Faces view tree at
        // RequestScope.
        saveBridgeRequestScopeData(context, scopeId, excludedAttributes);

        // Finalize the action response -- key here is the reliance on
        // ExternalContext.encodeActionURL to migrate info encoded
        // in the actionURL constructed from the target of this
        // navigation
        // into the ActionResponse so it can be decoded in the
        // asscoicated portlet render.

        finalizeActionResponse(context);

      }
    }
    catch (Exception e)
    {
      context.getExternalContext().log("Exception thrown in doFacesRequest:action", e);
      if (!(e instanceof BridgeException))
      {
        Throwable rootCause = e.getCause();
        throw new BridgeException(e.getMessage(), rootCause);
      }
      else
      {
        throw (BridgeException) e;
      }
    }
    finally
    {
      if (context != null)
      {
        m = context.getExternalContext().getRequestMap();
        context.release();
      }
    }

    logMap("Action completed: ", m);
  }

  public void doFacesRequest(RenderRequest request, RenderResponse response) throws BridgeException
  {

    // Set the Portlet lifecycle phase as a request attribute so its
    // available to Faces extensions -- allowing that code to NOT rely on
    // instanceof which can fail if a portlet container uses a single class
    // to implement both the action and render request/response objects
    request.setAttribute(Bridge.PORTLET_LIFECYCLE_PHASE, Bridge.PortletPhase.RenderPhase);

    // Set the FacesServletMapping attribute so the ExternalContext can
    // pick it up and use it to reverse map viewIds to paths
    if (mFacesMappings != null)
    {
      request.setAttribute(PortletExternalContextImpl.FACES_MAPPING_ATTRIBUTE, mFacesMappings);
    }

    FacesContext context = null;
    try
    {
      // Get the FacesContext instance for this request
      Lifecycle lifecycle = getLifecycle();
      context = getFacesContextFactory().getFacesContext(mPortletConfig, request, response,
                                                         lifecycle);
      ExternalContext extCtx = context.getExternalContext();

      logMap("Received RenderParams: ", context.getExternalContext().getRequestParameterValuesMap());

      // Use request from ExternalContext in case its been wrapped by an
      // extension
      RenderRequest extRequest = (RenderRequest) extCtx.getRequest();

      String scopeId = extRequest.getParameter(REQUEST_SCOPE_ID_RENDER_PARAM);

      if (restoreBridgeRequestScopeData(context, scopeId))
      {
        // Because the Bridge is required to always save/restore the
        // VIEW_STATE
        // parameter -- always attempt a restore
        extRequest = restoreActionParams(context);

        // only restores if first render after action
        // afterwards not restored from Bridge request scope
        // rather its saved/restored by Faces.
        restoreFacesView(context, scopeId);
      }

      // Ensure the ContentType is set before rendering
      if (extCtx.getResponseContentType() == null)
      {
        response.setContentType(extRequest.getResponseContentType());
      }

      // ensure that isPostback attribute set if VIEW_STATE param exists
      if (extCtx.getRequestParameterValuesMap().containsKey(ResponseStateManager.VIEW_STATE_PARAM))
      {
        extCtx.getRequestMap().put(Bridge.IS_POSTBACK_ATTRIBUTE, Boolean.TRUE);
      }

      // Note: if the scope wasn't restored then the Faces
      // FACES_VIEW_STATE
      // parameter will not have been carried into this render and hence
      // default Faces impls will not see this render as occuring in a
      // in a postback (isPostback() will return false. This means Faces
      // will create a new Tree instead of restoring one -- the semantics
      // one should get if the Bridge can't access its requestScope.

      // if the requestScope restored the ViewRoot then this must be
      // the first render after the action -- hence the tree isn't yet
      // stored/managed by Faces -- we can merely render it
      if (context.getViewRoot() == null)
      {
        // add self as PhaseListener to prevent action phases from
        // executing
        lifecycle.addPhaseListener(this);
        try
        {
          lifecycle.execute(context);
        }
        catch (Exception e)
        {
          ;
        }
        finally
        {
          lifecycle.removePhaseListener(this);
        }
      }
      getLifecycle().render(context);

    }
    catch (Exception e)
    {
      context.getExternalContext().log("Exception thrown in doFacesRequest:render", e);
      if (!(e instanceof BridgeException))
      {
        Throwable rootCause = e.getCause();
        throw new BridgeException(e.getMessage(), rootCause);
      }
      else
      {
        throw (BridgeException) e;
      }
    }
    finally
    {
      if (context != null)
      {
        context.release();
      }
    }
  }

  public void destroy()
  {
    // remove any scopes being managed for this portlet
    // Each scope has a per portlet prefix -- pass in the prefix
    // constructed by adding the prefix to an empty string.
    // removeRequestScopes(qualifyScopeId(""));

    mPortletConfig = null;
  }

  /**
   * ELContextListener impl
   */
  public void contextCreated(ELContextEvent ece)
  {
    // Add the portletConfig to the ELContext so it is evaluated
    ELContext elContext = ece.getELContext();

    // Only add if not already there
    if (elContext.getContext(PortletConfig.class) == null)
    {
      elContext.putContext(PortletConfig.class, mPortletConfig);
    }
  }

  private FacesContextFactory getFacesContextFactory() throws BridgeException
  {
    try
    {
      if (mFacesContextFactory == null)
      {
        mFacesContextFactory = (FacesContextFactory) FactoryFinder
                                                                  .getFactory(FactoryFinder.FACES_CONTEXT_FACTORY);
      }
      return mFacesContextFactory;
    }
    catch (FacesException e)
    {
      Throwable rootCause = e.getCause();
      throw new BridgeException(e.getMessage(), rootCause);
    }
  }

  private Lifecycle getLifecycle() throws BridgeException
  {
    try
    {
      if (mLifecycle == null)
      {
        LifecycleFactory lifecycleFactory = (LifecycleFactory) FactoryFinder
                                                                            .getFactory(FactoryFinder.LIFECYCLE_FACTORY);
        String lifecycleId = mPortletConfig.getPortletContext()
                                           .getInitParameter(FacesServlet.LIFECYCLE_ID_ATTR);
        if (lifecycleId == null)
        {
          lifecycleId = LifecycleFactory.DEFAULT_LIFECYCLE;
        }

        mLifecycle = lifecycleFactory.getLifecycle(lifecycleId);
      }
      return mLifecycle;
    }
    catch (FacesException e)
    {
      Throwable rootCause = e.getCause();
      throw new BridgeException(e.getMessage(), rootCause);
    }
  }

  private void saveFacesView(FacesContext context)
  {

    // first save any current Faces messages in the viewRoot
    saveFacesMessageState(context);

    // now place the viewRoot in the request scope
    Map requestMap = context.getExternalContext().getRequestMap();
    requestMap.put(FACES_VIEWROOT, context.getViewRoot());
  }

  private void restoreFacesView(FacesContext context, String scopeId)
  {
    Map requestMap = context.getExternalContext().getRequestMap();
    UIViewRoot viewRoot = (UIViewRoot) requestMap.get(FACES_VIEWROOT);
    if (viewRoot != null)
    {
      context.setViewRoot(viewRoot);
      // remove from current Request Scope and the saved Bridge Request
      // Scope
      requestMap.remove(FACES_VIEWROOT);
      removeFromBridgeRequestScopeData(context, scopeId, FACES_VIEWROOT);
    }
    restoreFacesMessageState(context);
    // Don't remove the messages as Faces doesn't save these during render
  }

  private void saveActionParams(FacesContext context)
  {
    // Always preserve the FACES_VIEW_STATE parameter as per spec.
    // If portlet requests it, also preserve the rst of them.
    ExternalContext ec = context.getExternalContext();
    Map requestMap = ec.getRequestMap();
    Map requestParameterMap = ec.getRequestParameterValuesMap();
    if (!mPreserveActionParams)
    {
      if (requestMap != null && requestParameterMap != null
          && requestParameterMap.containsKey(ResponseStateManager.VIEW_STATE_PARAM))
      {
        HashMap m = new HashMap(1);
        m.put(ResponseStateManager.VIEW_STATE_PARAM,
              requestParameterMap.get(ResponseStateManager.VIEW_STATE_PARAM));
        requestMap.put(REQUEST_PARAMETERS, m);
      }
    }
    else
    {
      // place the parameter map in the portlet request scope
      // so it will be promoted into the Bridge's request scope and hence
      // be available during render.
      requestMap.put(REQUEST_PARAMETERS, requestParameterMap);
    }
  }

  private RenderRequest restoreActionParams(FacesContext context)
  {
    // this is a little trickier then saving because there is no
    // corresponding set. Instead we wrap the request object and set it
    // on the externalContext.
    ExternalContext ec = context.getExternalContext();
    // Note: only available/restored if this scope was restored.
    Map m = (Map) ec.getRequestMap().get(REQUEST_PARAMETERS);

    // ensures current request returned if nothing to restore/wrap
    RenderRequest wrapped = (RenderRequest) ec.getRequest();
    if (m != null && !m.isEmpty())
    {
      wrapped = new BridgeRenderRequestWrapper(wrapped, m);
      ec.setRequest(wrapped);
    }
    return wrapped;
  }

  public void saveFacesMessageState(FacesContext context)
  {
    Iterator messages;
    // get the messages from Faces Context
    Iterator clientIds = context.getClientIdsWithMessages();
    if (clientIds.hasNext())
    {
      FacesMessageState state = new FacesMessageState();
      while (clientIds.hasNext())
      {
        String clientId = (String) clientIds.next();
        messages = context.getMessages(clientId);
        while (messages.hasNext())
        {
          state.addMessage(clientId, (FacesMessage) messages.next());
        }
      }

      context.getExternalContext().log("PortletPhaseListenerImpl.saveFacesMessageState()");

      // save state in ViewRoot attributes
      Map requestMap = context.getExternalContext().getRequestMap();
      requestMap.put(FACES_MESSAGES, state);
    }
  }

  public void restoreFacesMessageState(FacesContext context)
  {
    // Only restore for Render request
    if (context.getExternalContext().getRequest() instanceof RenderRequest)
    {
      Map map = context.getExternalContext().getRequestMap();

      // restoring FacesMessages
      FacesMessageState state1 = (FacesMessageState) map.get(FACES_MESSAGES);

      if (state1 != null)
      {
        context.getExternalContext().log("PortletPhaseListenerImpl.restoreFacesMessageState()");
        Iterator messages;
        Iterator clientIds = state1.getClientIds();
        while (clientIds.hasNext())
        {
          String clientId = (String) clientIds.next();
          messages = state1.getMessages(clientId);
          while (messages.hasNext())
          {
            context.addMessage(clientId, (FacesMessage) messages.next());
          }
        }
      }
    }
  }

  private String initBridgeRequestScope(ActionResponse response)
  {

    // Generate an RMI UID, which is a unique identifier WITHIN the local
    // host. This will be used as the new lifecyleID
    UID uid = new UID();
    String requestScopeId = qualifyScopeId(uid.toString());

    // set in response render parameter so will receive in future calls
    // however don't store internally until there is specific state to
    // manage
    response.setRenderParameter(REQUEST_SCOPE_ID_RENDER_PARAM, requestScopeId);

    return requestScopeId;
  }

  private void saveBridgeRequestScopeData(FacesContext context, String scopeId, String[] excludeList)
  {

    // TODO -- check config setting and if stipulated preserve ActionParams

    // Store the RequestMap @ the bridge's request scope
    putBridgeRequestScopeData(scopeId, copyRequestMap(context.getExternalContext().getRequestMap(),
                                                      excludeList));

    // flag the data so can remove it if the session terminates
    // as its unlikely useful if the session disappears
    watchScope(context, scopeId);
  }

  private void putBridgeRequestScopeData(String scopeId, Object o)
  {
    PortletContext portletContext = mPortletConfig.getPortletContext();

    // Get the request scope lock -- because its added during init it should
    // always be there.
    synchronized (portletContext.getAttribute(REQUEST_SCOPE_LOCK))
    {
      // get the managedScopeMap
      LRUMap requestScopeMap = (LRUMap) portletContext.getAttribute(REQUEST_SCOPE_MAP);

      if (requestScopeMap == null)
      {
        // see if portlet has defined how many requestScopes to manage
        // for this portlet
        Integer managedScopes = (Integer) portletContext
                                                        .getAttribute(Bridge.MAX_MANAGED_REQUEST_SCOPES);
        if (managedScopes == null || managedScopes.intValue() <= 0)
        {
          managedScopes = sDefaultMaxManagedRequestScopes;
        }

        requestScopeMap = new LRUMap(managedScopes.intValue());
        portletContext.setAttribute(REQUEST_SCOPE_MAP, requestScopeMap);
      }

      logMap("Saving RequestScope @ requestScopeId: " + scopeId, (Map) o);
      requestScopeMap.put(scopeId, o);
    }
  }

  private Map copyRequestMap(Map m, String[] excludeList)
  {
    HashMap copy = new HashMap(m.size());

    Set keySet = m.keySet();
    if (keySet != null)
    {
      Iterator keys = keySet.iterator();
      while (keys != null && keys.hasNext())
      {
        String requestAttrKey = (String) keys.next();
        Object requestAttrValue = m.get(requestAttrKey);
        // TODO -- restore the ACTION PARAMS if there

        // Don't copy any of the portlet or Faces objects
        if (!inExcludeList(excludeList, requestAttrKey)
            && !contextObject(requestAttrKey, requestAttrValue))
        {
          copy.put(requestAttrKey, requestAttrValue);
        }
      }
    }
    return copy;
  }

  private boolean inExcludeList(String[] excludeList, String key)
  {
    if (excludeList == null || excludeList.length <= 0)
    {
      return false;
    }
    for (String element : excludeList)
    {
      if (element.equals(key))
      {
        return true;
      }
    }
    return false;
  }

  private String[] getExcludedAttributes(PortletRequest request)
  {
    String[] names = null;

    // first count them to allocate the array
    int i = 0, count = 0;
    Enumeration e = request.getAttributeNames();
    if (e == null)
    {
      return null;
    }

    while (e.hasMoreElements())
    {
      e.nextElement();
      count += 1;
    }

    names = new String[count];
    e = request.getAttributeNames();
    if (e == null)
    {
      return null;
    }

    while (e.hasMoreElements() && i < count)
    {
      names[i++] = (String) e.nextElement();
    }
    return names;
  }

  private boolean contextObject(String s, Object o)
  {
    return o instanceof PortletConfig || o instanceof PortletContext || o instanceof PortletRequest
           || o instanceof PortletResponse || o instanceof PortletSession
           || o instanceof PortletPreferences || o instanceof PortalContext
           || o instanceof FacesContext || o instanceof ExternalContext
           || o instanceof ServletConfig || o instanceof ServletContext
           || o instanceof ServletRequest || o instanceof ServletResponse
           || o instanceof HttpSession || s.startsWith("javax.servlet.include");
  }

  private void logMap(String message, Map m)
  {
    PortletContext context = mPortletConfig.getPortletContext();

    context.log(message);
    Set keySet = m.keySet();
    if (keySet != null)
    {
      Iterator keys = keySet.iterator();
      while (keys != null && keys.hasNext())
      {
        String requestAttrKey = (String) keys.next();
        context.log("     Map entry: " + requestAttrKey);
      }
    }
    else
    {
      context.log("Map is empty");
    }

    context.log("logMap completed");
  }

  private boolean restoreBridgeRequestScopeData(FacesContext context, String scopeId)
                                                                                     throws BridgeException
  {

    PortletContext portletContext = mPortletConfig.getPortletContext();
    Map m = null;
    Map requestMap = context.getExternalContext().getRequestMap();

    if (scopeId == null)
    {
      return false;
    }

    // Get the data from the scope
    synchronized (portletContext.getAttribute(REQUEST_SCOPE_LOCK))
    {
      // get the managedScopeMap
      LRUMap requestScopeMap = (LRUMap) portletContext.getAttribute(REQUEST_SCOPE_MAP);
      // No scope for all renders before first action to this portletApp
      if (requestScopeMap == null)
      {
        return false;
      }

      m = (Map) requestScopeMap.get(scopeId);
      if (m == null)
      {
        return false;
      }

      logMap("Restoring scope: " + scopeId, m);
    }

    // Restore it as the RequestMap
    Set keySet = m.keySet();
    if (keySet != null)
    {
      Iterator keys = keySet.iterator();
      while (keys != null && keys.hasNext())
      {
        String requestAttrKey = (String) keys.next();
        Object requestAttrValue = m.get(requestAttrKey);

        requestMap.put(requestAttrKey, requestAttrValue);
      }
    }
    else
    {
      return false;
    }
    // Let's see what actually made it into the Map
    logMap("Restored RequestMap: ", context.getExternalContext().getRequestMap());

    return true;
  }

  private boolean removeFromBridgeRequestScopeData(FacesContext context, String scopeId, String key)
  {
    PortletContext portletContext = mPortletConfig.getPortletContext();
    Map m = null;
    Map requestMap = context.getExternalContext().getRequestMap();

    if (scopeId == null)
    {
      return false;
    }

    // Get the data from the scope
    synchronized (portletContext.getAttribute(REQUEST_SCOPE_LOCK))
    {
      // get the managedScopeMap
      LRUMap requestScopeMap = (LRUMap) portletContext.getAttribute(REQUEST_SCOPE_MAP);
      // No scope for all renders before first action to this portletApp
      if (requestScopeMap == null)
      {
        return false;
      }

      m = (Map) requestScopeMap.get(scopeId);
      if (m != null)
      {
        return m.remove(key) != null;
      }

    }

    return false;
  }

  /*
   * Takes in the scopeId and prefixes it with portletName: This is done so we can later remove this
   * portlet's managed scopes when needed
   */

  private String qualifyScopeId(String scopeId)
  {
    StringBuffer sb = new StringBuffer(mPortletConfig.getPortletName());
    sb.append(':');
    sb.append(scopeId);
    return sb.toString();
  }

  private void watchScope(FacesContext context, String scopeId)
  {
    PortletSession session = (PortletSession) context.getExternalContext().getSession(true);
    if (session != null)
    {
      RequestScopeListener scopeListener = (RequestScopeListener) session
                                                                         .getAttribute(REQUEST_SCOPE_LISTENER);
      if (scopeListener == null)
      {
        // only store the qualified prefix
        // if invalidated we walk the entire REQUEST_SCOPE Map and
        // remove
        // every scope that starts with this prefix.
        session.setAttribute(REQUEST_SCOPE_LISTENER, new RequestScopeListener(qualifyScopeId("")));
      }
    }
  }

  private void finalizeActionResponse(FacesContext context) throws IOException
  {

    // We rely on Faces ExternalContext.encodeActionURL to do the heavy
    // lifting here. First we construct a true actionURL using the viewId
    // for the view that is the target of the navigation. Then we call
    // encodeActionURL passing this URL. encodeActionURL encodes into
    // ActionResponse sufficient information (pulled from the supplied
    // actionURL) so that it (the EXteranlContext) can decode the
    // information
    // in the subsequent render request(s).

    ViewHandler viewHandler = context.getApplication().getViewHandler();
    String actionURL = viewHandler.getActionURL(context, context.getViewRoot().getViewId());
    String encodedActionURL = context.getExternalContext().encodeActionURL(actionURL);

    // Strictly speaking this is a redundant call (noop) as
    // ExternalContext.redirect() JSR 301 rules require redirects of
    // URLs containing viewIds (aka actionURLs) to be handled as
    // regular Faces navigation not full client redirects. Its
    // included here primarily to ensure that redirect is implemented
    // correctly.
    context.getExternalContext().redirect(encodedActionURL);
  }

  /* Implement the PhaseListener methods */

  public PhaseId getPhaseId()
  {
    return PhaseId.RESTORE_VIEW;
  }

  public void beforePhase(PhaseEvent event)
  {
    // do nothing
    return;
  }

  public void afterPhase(PhaseEvent event)
  {
    // only set renderresponse if in RESTORE_VIEW phase
    if (event.getPhaseId() == PhaseId.RESTORE_VIEW)
    {
      event.getFacesContext().renderResponse();
    }
  }

  private final class LRUMap extends LinkedHashMap
  {

    /**
     * 
     */
    private static final long serialVersionUID = 4372455368577337965L;
    private int               mMaxCapacity;

    public LRUMap(int maxCapacity)
    {
      super(maxCapacity, 1.0f, true);
      mMaxCapacity = maxCapacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry eldest)
    {
      return size() > mMaxCapacity;
    }

  }

  // TODO: Should we store these as attributes of the ViewTree??? It would
  // work as
  // everything is serializable. -- Issue is we need to implement a
  // PhaseListener to
  // to deal with this -- at the moment I prefer to isolate the Faces
  // extensions from this
  // detail and leave it all in this controller part.

  private final class FacesMessageState implements Serializable
  {
    /**
     * 
     */
    private static final long serialVersionUID = 8438070672451887050L;
    // For saving and restoring FacesMessages
    private Map               mMessages        = new HashMap();       // key=clientId;

    // value=FacesMessages

    public void addMessage(String clientId, FacesMessage message)
    {
      List list = (List) mMessages.get(clientId);
      if (list == null)
      {
        list = new ArrayList();
        mMessages.put(clientId, list);
      }
      list.add(message);
    }

    public Iterator getMessages(String clientId)
    {
      List list = (List) mMessages.get(clientId);
      if (list != null)
      {
        return list.iterator();
      }
      else
      {
        return Collections.EMPTY_LIST.iterator();
      }
    }

    public Iterator getClientIds()
    {
      return mMessages.keySet().iterator();
    }
  }

  private final class RequestScopeListener implements HttpSessionBindingListener
  {
    String mScopePrefix = null;

    public RequestScopeListener(String scopePrefix)
    {
      mScopePrefix = scopePrefix;
    }

    public void valueBound(HttpSessionBindingEvent event)
    {

    }

    public void valueUnbound(HttpSessionBindingEvent event)
    {
      // Call is in the BridgeImpl class
      // removeRequestScopes((String)event.getValue());
    }

  }
}
