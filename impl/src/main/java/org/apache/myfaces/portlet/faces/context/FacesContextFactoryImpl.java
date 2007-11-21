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

import javax.faces.FacesException;
import javax.faces.context.FacesContext;
import javax.faces.context.FacesContextFactory;
import javax.faces.lifecycle.Lifecycle;
import javax.portlet.PortletConfig;
import javax.portlet.PortletRequest;
import javax.portlet.PortletResponse;
import javax.portlet.faces.Bridge;
import javax.portlet.faces.BridgeUtil;
import javax.servlet.ServletRequest;

/**
 * A factory object that creates (if needed) and returns new FacesContext instance for running in
 * portlet environment (PortletFacesContextImpl)
 * 
 * The class is defined in &lt;faces-context-factory&gt; tag in faces-config.xml
 */
public class FacesContextFactoryImpl extends FacesContextFactory
{
  private FacesContextFactory mHandler;

  public FacesContextFactoryImpl(FacesContextFactory handler)
  {
    mHandler = handler;
  }

  @Override
  public FacesContext getFacesContext(Object config, Object request, Object response,
                                      Lifecycle lifecycle) throws FacesException
  {
    // if in portlet environment -- do a portlet container neutral test
    // first in case we are packaged in a web app that isn't deployed 
    // on a portlet container/as a portlet. Note:  can't use the BridgeUtil
    // method as that call requires the facesContext to exist.
    if (isPortletRequest(request))
    {
      // make sure they passed the right objects
      if (config instanceof PortletConfig && request instanceof PortletRequest
        && response instanceof PortletResponse)
      {
        return new PortletFacesContextImpl(
                                         new PortletExternalContextImpl((PortletConfig) config,
                                                                        (PortletRequest) request,
                                                                        (PortletResponse) response),
                                         lifecycle);
      }
      else
      {
        throw new FacesException("getFacesContext failed: Running in a portlet request butnot passed portlet objects");
      }
    }
    else
    {
      // otherwise, delegate
      return mHandler.getFacesContext(config, request, response, lifecycle);
    }
  }
  
  
  private boolean isPortletRequest(Object request) 
  {
    // could be either a servlet or portlet request object (or both)
    // Check servlet side first in case we are packaged in an application
    // that is running as a servlet in an environment that doesn't contain
    // a portlet container.
    if (request instanceof ServletRequest)
    {
      ServletRequest sr = (ServletRequest) request;
      Bridge.PortletPhase phase = (Bridge.PortletPhase) sr.getAttribute(Bridge.PORTLET_LIFECYCLE_PHASE);
      return (phase != null);
    }
    else if (request instanceof PortletRequest)
    {
      return true;
    }
      
    return false;
  }
  
}
