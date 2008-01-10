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

import java.lang.reflect.Method;

import java.net.URL;

import java.rmi.server.UID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

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
import javax.portlet.faces.annotation.BridgePreDestroy;
import javax.portlet.faces.annotation.BridgeRequestScopeAttributeAdded;
import javax.portlet.faces.annotation.ExcludeFromManagedRequestScope;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestAttributeEvent;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;

import org.apache.myfaces.portlet.faces.bridge.wrapper.BridgeRenderRequestWrapper;
import org.apache.myfaces.portlet.faces.context.PortletExternalContextImpl;
import org.apache.myfaces.portlet.faces.util.config.FacesConfigurationProcessor;
import org.apache.myfaces.portlet.faces.util.config.WebConfigurationProcessor;

public class BridgeImpl
  implements Bridge, ELContextListener, PhaseListener, ServletRequestAttributeListener
{
	private static final long	serialVersionUID	= 5807626987246270989L;

	// public so PortletStateManager can see/use
  public static final String UPDATED_VIEW_STATE_PARAM = "org.apache.myfaces.portlet.faces.updatedViewStateParam";

  private static final String REQUEST_SCOPE_LOCK = "org.apache.myfaces.portlet.faces.requestScopeLock";
  private static final String REQUEST_SCOPE_MAP = "org.apache.myfaces.portlet.faces.requestScopeMap";
  private static final String REQUEST_SCOPE_LISTENER = "org.apache.myfaces.portlet.faces.requestScopeWatch";
  private static final String FACES_VIEWROOT = "org.apache.myfaces.portlet.faces.facesViewRoot";
  private static final String FACES_MESSAGES = "org.apache.myfaces.portlet.faces.facesMessages";
  private static final String REQUEST_PARAMETERS = "org.apache.myfaces.portlet.faces.requestParameters";
  private static final String PREEXISTING_ATTRIBUTE_NAMES = "org.apache.myfaces.portlet.faces.preExistingAttributeNames";
  private static final String REQUEST_SCOPE_ID_RENDER_PARAM = "_bridgeRequestScopeId";
  private static final int DEFAULT_MAX_MANAGED_REQUEST_SCOPES = 100;

  private Boolean mPreserveActionParams = false;
  private List<String> mExcludedRequestAttributes = null;

  private PortletConfig mPortletConfig = null;
  private FacesContextFactory mFacesContextFactory = null;
  private Lifecycle mLifecycle = null;
  private List<String> mFacesMappings = null;


  public BridgeImpl()
  {
    // everything gets done in the init call.
  }

  public void init(PortletConfig config)
    throws BridgeException
  {
  	//TODO: Should we throw an exception if the bridge is already initialized?
  	
    mPortletConfig = config;
    PortletContext portletContext = mPortletConfig.getPortletContext();

    // get preserveActionParams and excludedAttributes configuration settings.
    mPreserveActionParams = (Boolean) portletContext.getAttribute(Bridge.BRIDGE_PACKAGE_PREFIX + mPortletConfig.getPortletName() + 
                                            "." + Bridge.PRESERVE_ACTION_PARAMS);
    
    mExcludedRequestAttributes = (List <String>) portletContext.getAttribute(Bridge.BRIDGE_PACKAGE_PREFIX + mPortletConfig.getPortletName() + 
                                            "." + Bridge.EXCLUDED_REQUEST_ATTRIBUTES);
    if (mExcludedRequestAttributes != null)
    {
      // copy the list as we may be adding to it and don't want to worry that this might be immutable
      mExcludedRequestAttributes = new ArrayList(mExcludedRequestAttributes);     
    }
    else
    {
      // Otherwise create an empty list
      mExcludedRequestAttributes = new ArrayList(5);
    }
   
    // Read excludedAttributes that may be defined in any face-config.xml
    readExcludedAttributesFromFacesConfig(portletContext, mExcludedRequestAttributes);

    // Set up the synchronziation object for the RequestScopeMap as we don't
    // want to sync on the PortletContext because its too broad. Note:
    // needed
    // because we not only need to sync the Map but also creating the Map
    // and
    // putting it in the PortletContext. Hence the sync object allows us
    // to limit syncronizing the PortletContext to once per portlet (init
    // time);
    
    // TODO: What about synching on a static object or using a class lock?
    //       Perhaps even the LRUMap itself if said map is a singleton?
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
    ApplicationFactory appFactory = 
      (ApplicationFactory) FactoryFinder.getFactory(FactoryFinder.APPLICATION_FACTORY);
    Application app = appFactory.getApplication();
    app.addELContextListener(this);

    // Process and cache the FacesServlet mappings for use by
    // ExternalContext
    WebConfigurationProcessor webConfig = new WebConfigurationProcessor(portletContext);
    mFacesMappings = webConfig.getFacesMappings();
    if (mFacesMappings == null || mFacesMappings.size() == 0)
    {
      throw new BridgeException("BridgeImpl.init(): unable to determine Faces servlet web.xml mapping.");
    }
    for (int i = 0; i < mFacesMappings.size(); i++)
    {
      portletContext.log("Mapping: " + mFacesMappings.get(i));
    }
  }

  public void doFacesRequest(ActionRequest request, ActionResponse response)
    throws BridgeException
  {
    // Set the Portlet lifecycle phase as a request attribute so its
    // available to Faces extensions -- allowing that code to NOT rely on
    // instanceof which can fail if a portlet container uses a single class
    // to implement both the action and render request/response objects
    request.setAttribute(Bridge.PORTLET_LIFECYCLE_PHASE, Bridge.PortletPhase.ACTION_PHASE);

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
    List<String> preExistingAttributes = getRequestAttributes(request);
    // place on the request for use here and in the servletRequestAttributeListener
    if (preExistingAttributes != null)
    {
      request.setAttribute(PREEXISTING_ATTRIBUTE_NAMES, preExistingAttributes);
    }

    FacesContext context = null;
    String scopeId = null;
    try
    {
      // Get the FacesContext instance for this request
      context = 
          getFacesContextFactory().getFacesContext(mPortletConfig, request, response, getLifecycle());

      // Each action starts a new "action lifecycle"
      // The Bridge preserves request scoped data and if so configured
      // Action Parameters for the duration of an action lifecycle
      scopeId = initBridgeRequestScope(request, response);

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
        saveBridgeRequestScopeData(context, scopeId, preExistingAttributes);

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
      dumpScopeId(scopeId, "ACTION_PHASE");
      // our servletrequestattributelistener uses this as an indicator of whether 
      // its actively working on a request -- remove it to indicate we are done
      request.removeAttribute(Bridge.PORTLET_LIFECYCLE_PHASE);
      if (context != null)
      {
        context.release();
      }
    }
  }
  
  private void dumpScopeId(String scopeId, String phase)
  {
    // Get the data from the scope
    PortletContext ctx = mPortletConfig.getPortletContext();
    ctx.log("dumpScopeId: " + phase);
    synchronized (ctx.getAttribute(REQUEST_SCOPE_LOCK))
    {
      // get the managedScopeMap
      LRUMap requestScopeMap = (LRUMap) ctx.getAttribute(REQUEST_SCOPE_MAP);
      // No scope for all renders before first action to this portletApp
      if (requestScopeMap == null)
      {
        ctx.log("There are No saved scoped.  Can't match: "+ scopeId);
        return;
      }

      Map<String, Object> m = requestScopeMap.get(scopeId);
      if (m == null)
      {
        ctx.log("Can't match scope: "+ scopeId);
        return;
      }
      
      Set<Map.Entry<String,Object>> set = m.entrySet();
      Iterator<Map.Entry<String,Object>> i = set.iterator();
      ctx.log("Elements in scope: " + scopeId);
      while (i.hasNext())
      {
        Map.Entry<String,Object> entry = i.next();
        ctx.log("     " + entry.getKey());
      }
      ctx.log("end dumpScopeId");
    }
       
  }

  public void doFacesRequest(RenderRequest request, RenderResponse response)
    throws BridgeException
  {
    String scopeId = null;
    
    // Set the Portlet lifecycle phase as a request attribute so its
    // available to Faces extensions -- allowing that code to NOT rely on
    // instanceof which can fail if a portlet container uses a single class
    // to implement both the action and render request/response objects
    request.setAttribute(Bridge.PORTLET_LIFECYCLE_PHASE, Bridge.PortletPhase.RENDER_PHASE);

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
      context = 
          getFacesContextFactory().getFacesContext(mPortletConfig, request, response, lifecycle);
      ExternalContext extCtx = context.getExternalContext();

      // Use request from ExternalContext in case its been wrapped by an
      // extension
      RenderRequest extRequest = (RenderRequest) extCtx.getRequest();

      scopeId = extRequest.getParameter(REQUEST_SCOPE_ID_RENDER_PARAM);

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
          // When exception occurs remove stored scope so don't
          // get stuck replaying the error when/if user refreshes
          if (scopeId != null)
          {
            removeRequestScopes(scopeId);
          }
        }
        finally
        {
          lifecycle.removePhaseListener(this);
        }
      }
      getLifecycle().render(context);
      
      // When we have navigated to this view between the action and render
      // the initial VIEW_STATE_PARAM reflects the actions view -- update
      // here to the one from this render so refresh will work.
      if (scopeId != null)
      {
        updateViewStateParam(context, scopeId);
      }

    }
    catch (Exception e)
    {
      // When exception occurs remove stored scope so don't
      // get stuck replaying the error when/if user refreshes
      if (scopeId != null)
      {
        removeRequestScopes(scopeId);
      }
      
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
      dumpScopeId(scopeId, "RENDER_PHASE");
      // our servletrequestattributelistener uses this as an indicator of whether 
      // its actively working on a request -- remove it to indicate we are done
      request.removeAttribute(Bridge.PORTLET_LIFECYCLE_PHASE);
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
    removeRequestScopes(qualifyScopeId(mPortletConfig.getPortletName(), null, null));

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
  
  /*
   * ServletRequestAttributeListener implementation
   */
  public void attributeAdded(ServletRequestAttributeEvent srae)
  {
    // use this phase attribute as an indicator of whether 
    // we are actively working on a request
    PortletPhase phase = (PortletPhase) srae.getServletRequest().getAttribute(Bridge.PORTLET_LIFECYCLE_PHASE);
    
    // do nothing if before/after bridge processing or in the render phase.
    // Don't care about render phase because we don't update/change the managed
    // scope based on changes during render.
    // ALSO: do nothing if not in the Bridge's managed request scope
    if (phase == null || phase == PortletPhase.RENDER_PHASE ||
        isExcludedFromBridgeRequestScope(srae.getName(),
                                               srae.getValue(),
                                               (List<String>)
                                                  srae.getServletRequest().getAttribute(PREEXISTING_ATTRIBUTE_NAMES)))
    {
      return;
    }
    
    // Otherwise -- see if the added attribute implements the bridge's 
    // BridgeRequestScopeAdded annotation -- call each method so annotated
    Object o = srae.getValue();
    Method[] methods = o.getClass().getMethods();
    for (int i = 0; i < methods.length; i++)
    {
      if (methods[i].isAnnotationPresent(BridgeRequestScopeAttributeAdded.class))
      {
        try
        {
          methods[i].invoke(o, null);
        }
        catch (Exception e)
        {
            // TODO: log problem
            // do nothing and forge ahead
            ;
        }
      }
    }
  }
  
  public void attributeRemoved(ServletRequestAttributeEvent srae)
  {
    // use this phase attribute as an indicator of whether 
    // we are actively working on a request
    PortletPhase phase = (PortletPhase) srae.getServletRequest().getAttribute(Bridge.PORTLET_LIFECYCLE_PHASE);
    
    // If in an action this means the attribute has been removed before we have
    // saved the action scope -- since the managed bean has been informed we are
    // running in a portlet environment it should have ignored the PreDestroy.
    // To make up for this we call its BridgePredestroy
    if (phase != null && phase == PortletPhase.ACTION_PHASE)
    {
      notifyPreDestroy(srae.getValue()); // in outerclass (BridgeImpl)
    }
  }
  
  public void attributeReplaced(ServletRequestAttributeEvent srae)
  {
    // use this phase attribute as an indicator of whether 
    // we are actively working on a request
    PortletPhase phase = (PortletPhase) srae.getServletRequest().getAttribute(Bridge.PORTLET_LIFECYCLE_PHASE);
    
    // If in an action this means the attribute has been replaced before we have
    // saved the action scope -- since the managed bean has been informed we are
    // running in a portlet environment it should have ignored the PreDestroy.
    // To make up for this we call its BridgePredestroy
    if (phase != null && phase == PortletPhase.ACTION_PHASE)
    {
      notifyPreDestroy(srae.getValue()); // in outerclass (BridgeImpl)
    }
  }

  private FacesContextFactory getFacesContextFactory()
    throws BridgeException
  {
    try
    {
      if (mFacesContextFactory == null)
      {
        mFacesContextFactory = 
            (FacesContextFactory) FactoryFinder.getFactory(FactoryFinder.FACES_CONTEXT_FACTORY);
      }
      return mFacesContextFactory;
    }
    catch (FacesException e)
    {
      Throwable rootCause = e.getCause();
      throw new BridgeException(e.getMessage(), rootCause);
    }
  }

  private Lifecycle getLifecycle()
    throws BridgeException
  {
    try
    {
      if (mLifecycle == null)
      {
        LifecycleFactory lifecycleFactory = 
          (LifecycleFactory) FactoryFinder.getFactory(FactoryFinder.LIFECYCLE_FACTORY);
        String lifecycleId = 
          mPortletConfig.getPortletContext().getInitParameter(FacesServlet.LIFECYCLE_ID_ATTR);
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
    Map<String, Object> requestMap = context.getExternalContext().getRequestMap();
    requestMap.put(FACES_VIEWROOT, context.getViewRoot());
  }

  private void restoreFacesView(FacesContext context, String scopeId)
  {
    Map<String, Object> requestMap = context.getExternalContext().getRequestMap();
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
    Map<String, Object> requestMap = ec.getRequestMap();
    Map<String, String[]> requestParameterMap = ec.getRequestParameterValuesMap();
    if (mPreserveActionParams == Boolean.FALSE)
    {
      if (requestMap != null && requestParameterMap != null && 
          requestParameterMap.containsKey(ResponseStateManager.VIEW_STATE_PARAM))
      {
        Map<String, String[]> m = new HashMap<String, String[]>(1);
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
  
  private void updateViewStateParam(FacesContext context, String scopeId)
  {
    
    // First make sure we have a value to update
    String updatedViewStateParam = (String) context.getExternalContext()
        .getRequestMap().get(UPDATED_VIEW_STATE_PARAM);
    
    if (updatedViewStateParam == null)
        return;
    
    // Otherwise we need to update/store this value in the scope
    PortletContext portletContext = mPortletConfig.getPortletContext();

    // Get the request scope lock -- because its added during init it should
    // always be there.
    synchronized (portletContext.getAttribute(REQUEST_SCOPE_LOCK))
    {
      // get the managedScopeMap
      LRUMap requestScopeMap = (LRUMap) portletContext.getAttribute(REQUEST_SCOPE_MAP);

      if (requestScopeMap == null)
      {
        // Have only done renders to this point -- so no scope to update
        return;
      }
      
      // now see if this scope is in the Map
      Map<String, Object> scopeMap = requestScopeMap.get(scopeId);
      if (scopeMap == null)
      {
        // Scope has been previously removed -- so no scope to update
        return;
      }

      // Prepare the value for storing as a preserved parameter
      // Store as an array of Strings with just one entry as per
      // portlet request
      String[] values = new String[1];
      values[0] = updatedViewStateParam;

      // Now get the RequestParameters from the scope
      @SuppressWarnings("unchecked")
      Map<String, String[]> requestParams = (Map<String, String[]>)scopeMap.get(REQUEST_PARAMETERS);
      
      if (requestParams == null) 
      {
        requestParams = new HashMap<String, String[]>(1);
        scopeMap.put(REQUEST_PARAMETERS, requestParams);
      }
      // finally update the value in the Map
      requestParams.put(ResponseStateManager.VIEW_STATE_PARAM, values);
    }
  }
  
  
  private LRUMap createRequestScopeMap(PortletContext portletContext) 
  {
    // see if portlet has defined how many requestScopes to manage
    // for this portlet
    int managedScopes = DEFAULT_MAX_MANAGED_REQUEST_SCOPES;
    
    String managedScopesSetting = 
    	portletContext.getInitParameter(Bridge.MAX_MANAGED_REQUEST_SCOPES);
    if (managedScopesSetting != null)
    {
      managedScopes = Integer.parseInt(managedScopesSetting);
    }
    
    return new LRUMap(managedScopes);
  }

  @SuppressWarnings("unchecked")
	private RenderRequest restoreActionParams(FacesContext context)
  {
    // this is a little trickier then saving because there is no
    // corresponding set. Instead we wrap the request object and set it
    // on the externalContext.
    ExternalContext ec = context.getExternalContext();
    // Note: only available/restored if this scope was restored.
    Map<String, String[]> m = (Map<String, String[]>) ec.getRequestMap().get(REQUEST_PARAMETERS);

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
    // get the messages from Faces Context
    Iterator<String> clientIds = context.getClientIdsWithMessages();
    if (clientIds.hasNext())
    {
      FacesMessageState state = new FacesMessageState();
      while (clientIds.hasNext())
      {
        String clientId = (String) clientIds.next();
        for(Iterator<FacesMessage> messages = context.getMessages(clientId);messages.hasNext();)
        {
        	state.addMessage(clientId, messages.next());
        }
      }
      // save state in ViewRoot attributes
      Map<String, Object> requestMap = context.getExternalContext().getRequestMap();
      requestMap.put(FACES_MESSAGES, state);
    }
  }

  public void restoreFacesMessageState(FacesContext context)
  {
    // Only restore for Render request
    if (context.getExternalContext().getRequest() instanceof RenderRequest)
    {
      Map<String, Object> map = context.getExternalContext().getRequestMap();

      // restoring FacesMessages
      FacesMessageState state = (FacesMessageState) map.get(FACES_MESSAGES);

      if (state != null)
      {
      	for(String clientId:state.getClientIds())
      	{
      		for(FacesMessage message:state.getMessages(clientId))
      		{
      			context.addMessage(clientId, message);
          }
        }
      }
    }
  }

  private String initBridgeRequestScope(ActionRequest request, ActionResponse response)
  {

    // Generate an RMI UID, which is a unique identifier WITHIN the local
    // host. This will be used as the new lifecyleID
    UID uid = new UID();
    String requestScopeId = qualifyScopeId(mPortletConfig.getPortletName(),
                                           request.getPortletSession(true).getId(),
                                           uid.toString());

    // set in response render parameter so will receive in future calls
    // however don't store internally until there is specific state to
    // manage
    response.setRenderParameter(REQUEST_SCOPE_ID_RENDER_PARAM, requestScopeId);

    return requestScopeId;
  }

  private void saveBridgeRequestScopeData(FacesContext context, String scopeId, 
                                          List<String> preExistingList)
  {

    // Store the RequestMap @ the bridge's request scope
    putBridgeRequestScopeData(scopeId, 
                              copyRequestMap(context.getExternalContext().getRequestMap(), preExistingList));

    // flag the data so can remove it if the session terminates
    // as its unlikely useful if the session disappears
    watchScope(context, scopeId);
  }

  @SuppressWarnings("unchecked")
  private void putBridgeRequestScopeData(String scopeId, Map<String, Object> o)
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
        requestScopeMap = createRequestScopeMap(portletContext);
        portletContext.setAttribute(REQUEST_SCOPE_MAP, requestScopeMap);
      }
      requestScopeMap.put(scopeId, o);
    }
  }

  private Map<String, Object> copyRequestMap(Map<String, Object> m, List<String> preExistingList)
  {
    Map<String, Object> copy = new HashMap<String, Object>(m.size());
     
  	for(Map.Entry<String, Object> entry:m.entrySet())
  	{
      // TODO -- restore the ACTION PARAMS if there

      // Don't copy any of the portlet or Faces objects
  		String key = entry.getKey();
  		Object value = entry.getValue();
  		if(!isExcludedFromBridgeRequestScope(key, value, preExistingList))
  		{
  			copy.put(key, value);
  		}
  	}
    return copy;
  }
  
  @SuppressWarnings("unchecked")
  private List<String> getRequestAttributes(PortletRequest request)
  {
  	return Collections.list((Enumeration<String>)request.getAttributeNames());
  }
  
  private boolean isExcludedFromBridgeRequestScope(String key, Object value, List<String> preExistingList)
  {
    return ((value.getClass().getAnnotation(ExcludeFromManagedRequestScope.class) != null) ||
         (preExistingList != null && preExistingList.contains(key)) ||
         isPreDefinedExcludedObject(key, value) ||
         isConfiguredExcludedAttribute(key));
  }

  private boolean isPreDefinedExcludedObject(String s, Object o)
  {
    return o instanceof PortletConfig || o instanceof PortletContext || 
      o instanceof PortletRequest || o instanceof PortletResponse || o instanceof PortletSession || 
      o instanceof PortletPreferences || o instanceof PortalContext || o instanceof FacesContext || 
      o instanceof ExternalContext || o instanceof ServletConfig || o instanceof ServletContext || 
      o instanceof ServletRequest || o instanceof ServletResponse || o instanceof HttpSession || 
      isInNamespace(s, "javax.portlet.") ||
      isInNamespace(s, "javax.portlet.faces.") ||
      isInNamespace(s, "javax.faces.") ||
      isInNamespace(s, "javax.servlet.") ||
      isInNamespace(s, "javax.servlet.include.") ||
      s.equals(PREEXISTING_ATTRIBUTE_NAMES);
    }
  
  private boolean isConfiguredExcludedAttribute(String s)
  {
    if (mExcludedRequestAttributes == null)
    {
      return false;
    }
    
    if (mExcludedRequestAttributes.contains(s))
    {
      return true;
    }
    
    // No direct match -- walk through this list and process namespace checks
    Iterator<String> i = mExcludedRequestAttributes.iterator();
    while (i.hasNext())
    {
      String exclude = i.next();
      if (exclude.endsWith("*"))
      {
        if (isInNamespace(s, exclude.substring(0, exclude.length() - 1)))
        {
          return true;
        }
      }
    }
    return false;
  }
      
  private boolean isInNamespace(String s, String namespace)
  {
    // This is a non-recursive check so s must be the result of removing the namespace.
    if (s.startsWith(namespace))
    {
    // extract entire namespace and compare
    s = s.substring(0, s.lastIndexOf('.') + 1);
    return s.equals(namespace);
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private boolean restoreBridgeRequestScopeData(FacesContext context, String scopeId)
    throws BridgeException
  {

    PortletContext portletContext = mPortletConfig.getPortletContext();
    Map<String, Object> m;
    Map<String, Object> requestMap = context.getExternalContext().getRequestMap();
    
    //TODO: Since this is a private method, is it easier to ensure scope id is not null here thus replacing this with
    //an assert
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

      m = requestScopeMap.get(scopeId);
      if (m == null)
      {
        return false;
      }
    }
    
    requestMap.putAll(m);
    return true;
  }

  private boolean removeFromBridgeRequestScopeData(FacesContext context, String scopeId, 
                                                   String key)
  {
    PortletContext portletContext = mPortletConfig.getPortletContext();
    Map<String, Object> m = null;

    //TODO: Since this is a private method, is it easier to ensure scope id is not null here thus replacing this with
    //an assert
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

      m = requestScopeMap.get(scopeId);
      if (m != null)
      {
        return m.remove(key) != null;
      }

    }

    return false;
  }

  /*
   * A scope is qualified first by the portlet this scope has been created for 
   * and then second by the specific session this scope is used in.  By doing
   * this we are able to remove this specific scope, all the scopes associated 
   * with a particular session, or all the scopes associated with a particular
   * portlet regardless of sessions.
   */

  private String qualifyScopeId(String portletId, String sessionId, String scopeId)
  {
    // a qualified scope Id must at a minimum be qualified by a portletId
    if (portletId == null) portletId = mPortletConfig.getPortletName();
    
    StringBuffer sb = new StringBuffer(portletId);
    sb.append(':');
    if (sessionId != null) 
    {
      sb.append(sessionId);
      sb.append(':');
      if (scopeId != null)
      {
        sb.append(scopeId);
      }
    }

    return sb.toString();
  }

  private void watchScope(FacesContext context, String scopeId)
  {
    PortletSession session = (PortletSession) context.getExternalContext().getSession(true);
    if (session != null)
    {
      RequestScopeListener scopeListener = 
        (RequestScopeListener) session.getAttribute(REQUEST_SCOPE_LISTENER);
      if (scopeListener == null)
      {
        // only store the qualified prefix
        // if invalidated we walk the entire REQUEST_SCOPE Map and
        // remove
        // every scope that starts with this prefix.
        session.setAttribute(REQUEST_SCOPE_LISTENER, new RequestScopeListener(
                                                       qualifyScopeId(mPortletConfig.getPortletName(),
                                                                      session.getId(),
                                                                      null)));
      }
    }
  }

  private void finalizeActionResponse(FacesContext context)
    throws IOException
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
  
  // notify this scope's attributes that they are being removed
  private void notifyPreDestroy(Map<String,Object> scope)
  {
    Set<Map.Entry<String,Object>>  s = scope.entrySet();
    Iterator<Map.Entry<String,Object>> i = s.iterator();
    while (i.hasNext())
    {
      notifyPreDestroy(i.next().getValue());
    }
  }
  
  // notify this scope's attributes that they are being removed
  private void notifyPreDestroy(Object o)
  {
    Method[] methods = o.getClass().getMethods();
    for (int m = 0; m < methods.length; m++)
    {
      if (methods[m].isAnnotationPresent(BridgePreDestroy.class))
      {
        try
        {
          methods[m].invoke(o, null);
        }
        catch (Exception e)
        {
            // TODO: log problem
            // do nothing and forge ahead
            ;
        }
      }
    }
  }

  private void removeRequestScopes(String scopePrefix)
  {

    // Get the RequestScope Map and remove all entries/scopes with this prefix
    PortletContext portletContext = mPortletConfig.getPortletContext();

    // Get the request scope lock -- because its added during init it should
    // always be there.
    Object lock = portletContext.getAttribute(REQUEST_SCOPE_LOCK);
    if (lock == null)
      return;

    synchronized (lock)
    {
      // get the managedScopeMap
      LRUMap requestScopeMap = (LRUMap) portletContext.getAttribute(REQUEST_SCOPE_MAP);

      if (requestScopeMap != null)
      {
      	Iterator<String> iterator = requestScopeMap.keySet().iterator();
      	while(iterator.hasNext())
      	{
      		String scopeId = iterator.next();
          if (scopeId != null && scopeId.startsWith(scopePrefix))
          {
          	iterator.remove();
          }
      	}
      }
    }
  }
  
  private void readExcludedAttributesFromFacesConfig(PortletContext context,
                                                     List<String> excludedAttributes)
  {
    FacesConfigurationProcessor processor = new FacesConfigurationProcessor(context);
    List<String> list = processor.getExcludedAttributes();
    
    if (list == null)
    {
      return;
    }
    
    ListIterator<String> i = (ListIterator<String>) list.listIterator();
    while (i.hasNext())
    {
      String attr = i.next();
      if (!excludedAttributes.contains(attr))
      {
        excludedAttributes.add(attr);
      }
    }
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

  private final class LRUMap
    extends LinkedHashMap<String, Map<String, Object>>
  {

    /**
     * 
     */
    private static final long serialVersionUID = 4372455368577337965L;
    private int mMaxCapacity;

    public LRUMap(int maxCapacity)
    {
      super(maxCapacity, 1.0f, true);
      mMaxCapacity = maxCapacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Map<String,Object>> eldest)
    {
      // manually remove the entry so we can ensure notifyPreDestroy is only
      // called once
      if (size() > mMaxCapacity)
      {
        // side effect of this call is to notify PreDestroy
        remove(eldest.getKey());
      }
      return false;
    }
    
    public Map<String,Object> remove(String key) 
    {
      dumpScopeId(key, "RemovePhase");
      Map<String,Object> o = super.remove(key);
      // notify attributes maintained in this object (map) they are going away
      // Method in the outer BridgeImpl class
      if (o != null) notifyPreDestroy(o);
      return o;
    }
    
    public Map<String,Object> put(String key, Map<String,Object> value)
    {
      Map<String,Object> o = super.put(key, value);
      // notify attributes maintained in this object (map) they are going away
      // Method in the outer BridgeImpl class
      if (o != null) notifyPreDestroy(o);
      return o;      
    }

  }

  // TODO: Should we store these as attributes of the ViewTree??? It would
  // work as
  // everything is serializable. -- Issue is we need to implement a
  // PhaseListener to
  // to deal with this -- at the moment I prefer to isolate the Faces
  // extensions from this
  // detail and leave it all in this controller part.

  private final class FacesMessageState
    implements Serializable
  {
    /**
     * 
     */
    private static final long serialVersionUID = 8438070672451887050L;
    // For saving and restoring FacesMessages
    private Map<String, List<FacesMessage>> mMessages = new HashMap<String, List<FacesMessage>>(); // key=clientId;

    // value=FacesMessages

    public void addMessage(String clientId, FacesMessage message)
    {
      List<FacesMessage> list = mMessages.get(clientId);
      if (list == null)
      {
        list = new ArrayList<FacesMessage>();
        mMessages.put(clientId, list);
      }
      list.add(message);
    }

    public List<FacesMessage> getMessages(String clientId)
    {
      List<FacesMessage> list = mMessages.get(clientId);
      if (list != null)
      {
        return list;
      }
      else
      {
        return Collections.emptyList();
      }
    }

    public Set<String> getClientIds()
    {
      return mMessages.keySet();
    }
  }

  private final class RequestScopeListener
    implements HttpSessionBindingListener
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
      removeRequestScopes(mScopePrefix);
    }

  }
}
