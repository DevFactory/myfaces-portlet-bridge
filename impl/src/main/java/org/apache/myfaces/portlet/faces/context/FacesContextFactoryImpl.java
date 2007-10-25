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
    // if in portlet environment
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
      // otherwise, delegate
      return mHandler.getFacesContext(config, request, response, lifecycle);
    }
  }
}
