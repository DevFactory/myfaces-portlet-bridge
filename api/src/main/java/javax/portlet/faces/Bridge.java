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

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletConfig;
import javax.portlet.PortletException;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.UnavailableException;

/**
 * The <CODE>Bridge</CODE> interface is used by a portlet to execute a JSF artifact. Its lifecycle
 * follows the pattern used by other web components such as portlets or servlets, namely:
 * <ul>
 * <li><code>init</code>: one time (per portlet) initialization. Usually invoked during portlet
 * <code>init</code> but may also occur lazily. Context is passed to the Bridge at initialization
 * via <code>PortletContext</code> attributes. See method description for details. </li>
 * <li><code>doFacesRequest</code>: called for each portlet request that is to be handled by
 * Faces. Must only be called after the bridge has been initialized. </li>
 * <li><code>destroy</code>: called to destroy this bridge instance. Usually invoked during
 * portlet <code>destroy</code> but may also occur earlier if the portlet decides to reclaim
 * resources. </li>
 * </ul>
 * <P>
 * Portlet developers are encouraged to allow deployers an ability to configure the particular
 * Bridge implementation it uses within a given deployment. This ensures a best fit solution for a
 * given application server, portlet container, and/or Faces environment. The specifics for this
 * configuation are undefined. Each portlet can define a preferred mechanism. Subclasses of
 * {@link GenericFacesPortlet} automatically inherit this behavior as it recognizes a defined
 * portlet initialization parameter.
 * <p>
 * Implementations of this <code>Bridge</code> interface are required to have a <code>code</code>
 * constructor.
 */

public interface Bridge
{

  // Base Bridge attribute/context parameter prefix
  public static final String BRIDGE_PACKAGE_PREFIX         = "javax.portlet.faces.";

  // Following are the names of context init parameters that control
  // Bridge behavior. These are specified in the web.xml

  public static final String MAX_MANAGED_REQUEST_SCOPES    = BRIDGE_PACKAGE_PREFIX
                                                             + "MAX_MANAGED_REQUEST_SCOPES";
  public static final String RENDER_POLICY                 = BRIDGE_PACKAGE_PREFIX
                                                              + "RENDER_POLICY";

  public static final String LIFECYCLE_ID                  = "javax.faces.LIFECYCLE_ID";

  // Attribute signifying whether this render is a postback or not.
  public static final String IS_POSTBACK_ATTRIBUTE         = BRIDGE_PACKAGE_PREFIX + "isPostback";

  // Special session attribute name to hold the application_scope in the
  // portlet_scope of the session so these are accessible as well.
  public static final String APPLICATION_SCOPE_MAP         = "javax.portlet.faces.ApplicationScopeMap";
  
  // Names for special QueryString parameters names the Bridge recognizes in
  // encodeActionURL as signifying to change the corresponding portlet values
  // in the resulting URL
  public static final String PORTLET_MODE_PARAMETER = BRIDGE_PACKAGE_PREFIX + "PortletMode";
  public static final String PORTLET_WINDOWSTATE_PARAMETER = BRIDGE_PACKAGE_PREFIX + "WindowState";
  public static final String PORTLET_SECURE_PARAMETER = BRIDGE_PACKAGE_PREFIX + "Secure";

  // Following are the names of context attributes that a portlet can set prior
  // to calling the bridge's init() method to control Bridge behavior.

  // These attributes are scoped to a specific portlet in the context
  // hence to acquire one must include the portlet name within attribute name:
  // BRIDGE_PACKAGE_PREFIX + context.getPortletName() + attributeName

  // if "true" indicates the bridge will preserve all the action params in its
  // request scope and restore them as parameters in the subsequent renders
  public static final String PRESERVE_ACTION_PARAMS        = "preserveActionParams";

  // allows a portlet to which request attributes the bridge excludes from its
  // managed request scope.
  public static final String EXCLUDED_REQUEST_ATTRIBUTES    = "excludedRequestAttributes";

  // Parameter that can be added to an ActionURL to signify it is a direct link
  // and hence shouldn't be encoded by encodeActionURL as an actionURL
  public static final String DIRECT_LINK                   = BRIDGE_PACKAGE_PREFIX + "DirectLink";

  // Session attribute pushed by bridge into session scope to give one access
  // to Application scope
  public static final String SESSION_APPLICATION_SCOPE_MAP = BRIDGE_PACKAGE_PREFIX
                                                             + "ApplicationScopeMap";

  // Request attribute pushed by bridge in renderView to indicate it can
  // handle a filter putting the AFTER_VIEW_CONTENT in a buffer on the request.
  // Allows rendering order to be preserved in jsps
  public static final String RENDER_CONTENT_AFTER_VIEW     = BRIDGE_PACKAGE_PREFIX
                                                             + "RenderContentAfterView";

  // Request attribute set by servlet filter in request/responseWrapper to
  // place the AFTER_VIEW_CONTENT in a buffer on the request.
  // Allows filter to transfer such content back to the bridge/renderView so
  // if can output in correct order. Should only be done if
  // RENDER_CONTENT_AFTER_VIEW request attribute is true.
  public static final String AFTER_VIEW_CONTENT            = BRIDGE_PACKAGE_PREFIX
                                                             + "AfterViewContent";

  // Following are names of request attributes a portlet must set before
  // calling the Bridge to process a request
  public static final String DEFAULT_VIEWID                = BRIDGE_PACKAGE_PREFIX
                                                             + "defaultViewId";

  // Following are the names of request attributes the Bridge must set before
  // acquiring its first FacesContext/FacesContextFactory in each request
  public static final String PORTLET_LIFECYCLE_PHASE       = BRIDGE_PACKAGE_PREFIX + "phase";

  public static final String PORTLET_ISNAMESPACED_PROPERTY = "X-JAVAX-PORTLET-IS-NAMESPACED";

  // The possible JSR168 portlet lifecycle phazses

  public static enum PortletPhase
  {
    ACTION_PHASE, RENDER_PHASE, ;
  }

  public static enum BridgeRenderPolicy
  {
    DEFAULT, ALWAYS_DELEGATE, NEVER_DELEGATE, ;
  }

  /**
   * Called by the portlet. It indicates that the bridge is being placed into service.
   * <p>
   * The portlet calls the <code>init</code> method exactly once before invoking other lifecycle
   * methods. Usually, done immediately after instantiating the bridge. The <code>init</code>
   * method must complete successfully before the bridge can receive any requests.
   * <p>
   * The portlet cannot place the bridge into service if the <code>init</code> method Throws a
   * <code>BridgeException</code>.
   * <p>
   * Initialization context is passed to bridge via <code>PortletContext</code> attributes. The
   * following attributes are defined:
   * <ul>
   * <li><code>javax.portlet.faces.encodeRedirectURL</code>: instructs the bridge to call
   * <code>ExternalContext.encodeActionURL()</code> before processing the redirect request. This
   * exists because some (newer) versions of JSF 1.2 call <code>encodeActionURL</code> before
   * calling <code>redirect</code> while others do not. This flag adjusts the behavior of the
   * bridge in accordance with the JSF 1.2 implementation it runs with.
   * <li><code>javax.portlet.faces.numManagedActionScopes</code>: defines the maximum number of
   * actionScopes this bridge preserves at any given time. Value is an integer. ActionScopes are
   * managed on a per Bridge class portlet context wide basis. As a typical portlet application uses
   * the same bridge implementation for all its Faces based portlets, this means that all
   * actionScopes are managed in a single bucket.<br>
   * For convenience this interface defines the <code>NUM_MANAGED_ACTIONSCOPES</code> constant.
   * <li><code>javax.faces.lifecycleID</code>: defines the Faces <code>Lifecycle</code> id
   * that bridge uses when acquiring the <code>Faces.Lifecycle</code> via which it executes the
   * request. As a context wide attribute, all bridge instances in this portlet application will use
   * this lifecyle.
   * <li><code>javax.portlet.faces.[portlet name].preserveActionParams</code>: instructs the
   * bridge to preserve action parameters in the action scope and represent them in subsequent
   * renders. Should be used only when binding to a Faces implementation that relies on accessing
   * such parameters during its render phase. As this is a portlet/bridge instance specific
   * attribute, the <code>PortletContext</code>attribute name is qualified by the portlet
   * instance name. This allows different portlets within the same portlet application to have
   * different settings.<br>
   * For convenience this interfaces defines a number of constants that simplifies constructing
   * and/or recognizing this name.
   * </ul>
   * 
   * @param config
   *          a <code>PortletConfig</code> object containing the portlet's configuration and
   *          initialization parameters
   * @exception PortletException
   *              if an exception has occurred that interferes with the portlet's normal operation.
   * @exception UnavailableException
   *              if the portlet cannot perform the initialization at this time.
   */
  public void init(PortletConfig config) throws BridgeException;

  /**
   * Called by the portlet when it wants the bridge to process an action request.
   * 
   * @param request
   *          the request object.
   * @param response
   *          the response object.
   * @throws BridgeDefaultViewNotSpecifiedException
   *           thrown if the request indicates to the Bridge that is should use the default ViewId
   *           and the portlet hasn't supplied one.
   * @throws BridgeException
   *           all other internal exceptions are converted to a BridgeException.
   */
  public void doFacesRequest(ActionRequest request, ActionResponse response)
                                                                            throws BridgeDefaultViewNotSpecifiedException,
                                                                            BridgeException;

  /**
   * Called by the portlet when it wants the bridge to process a render request.
   * 
   * @param request
   *          the request object.
   * @param response
   *          the response object.
   * @throws BridgeDefaultViewNotSpecifiedException
   *           thrown if the request indicates to the Bridge that is should use the default ViewId
   *           and the portlet hasn't supplied one.
   * @throws BridgeException
   *           all other internal exceptions are converted to a BridgeException.
   */
  public void doFacesRequest(RenderRequest request, RenderResponse response)
                                                                            throws BridgeDefaultViewNotSpecifiedException,
                                                                            BridgeException;

  /**
   * Called by the portlet to take the bridge out of service. Once out of service, the bridge must
   * be reinitialized before processing any further requests.
   */
  public void destroy();

}
