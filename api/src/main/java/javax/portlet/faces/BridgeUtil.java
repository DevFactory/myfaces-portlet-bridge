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

import java.util.Map;

import javax.faces.context.FacesContext;

import javax.portlet.faces.Bridge;

public class BridgeUtil
{
  public static boolean isPortletRequest() 
  {
    Map<String, Object> m = FacesContext.getCurrentInstance().getExternalContext().getRequestMap();
    Bridge.PortletPhase phase = (Bridge.PortletPhase) m.get(Bridge.PORTLET_LIFECYCLE_PHASE);
    if (phase != null)
    {
      return true;
    }
    else 
    {
      return false;
    }
  }
  
  public static Bridge.PortletPhase getPortletRequestPhase() 
  {
    Map<String, Object> m = FacesContext.getCurrentInstance().getExternalContext().getRequestMap();
    return (Bridge.PortletPhase) m.get(Bridge.PORTLET_LIFECYCLE_PHASE);
  }
  
}
