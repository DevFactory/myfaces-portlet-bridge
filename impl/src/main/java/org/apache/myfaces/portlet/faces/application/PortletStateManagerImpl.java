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

package org.apache.myfaces.portlet.faces.application;

import java.io.IOException;
import java.io.StringWriter;

import java.util.Map;

import javax.faces.application.StateManager;
import javax.faces.application.StateManagerWrapper;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;

import javax.faces.render.ResponseStateManager;

import javax.portlet.faces.BridgeUtil;

import org.apache.myfaces.portlet.faces.bridge.BridgeImpl;

public class PortletStateManagerImpl
  extends StateManagerWrapper
{
  private StateManager mDelegatee = null;
  
  public PortletStateManagerImpl(StateManager sm)
  {
    mDelegatee = sm;
  }
  
  /*
   * Override saveView so we can grab the returned state and place
   * it in the request scope so the Bridge can find it and use for subsequent
   * renders that occur in this managed bridge (action) request scope.
   * 
   * Basically what is going one here is that JSF either stores the entire state
   * or at least enough info to get back to its entire state in a hidden field
   * represented by the VIEW_STATE_PARAM.  In addition the existence of this
   * parameter in any given request is used to determine if one is running in a
   * postback or not.  
   * Because in the portlet environment renders occur in a separate request from
   * action we need to preserve this parameter in the first request after an 
   * action so JSF will know its in a postback.  However after the first render
   * following an action additional renders can occur (before the next action).
   * These renders also need to signal they are running in a postback by exposing
   * this parameter however the value should not be the one that was sent to the 
   * original action but rather the one that resulted after the last render.
   * 
   * To do this the Bridge has to catch the value that is being written into
   * the hidden field.  It does this by overriding writeState and replace the 
   * value its maintaining for the VIEW_STATE_PARAM with this value written as
   * a String.
   */
  public void writeState(FacesContext context, Object state)
    throws IOException
  {
    // Do nothing when not running in portlet request
    if (!BridgeUtil.isPortletRequest())
    {
      super.writeState(context, state);
      return;
    }

    // Replace current response writer so can grab what is written
    ResponseWriter oldRW = context.getResponseWriter();
    StringWriter stringWriter = new StringWriter(128);
    ResponseWriter newRW = oldRW.cloneWithWriter(stringWriter);
    context.setResponseWriter(newRW);
    
    super.writeState(context, state);
    
    // Restore real responsewriter
    context.setResponseWriter(oldRW);
    
    // Get written state
    newRW.flush();
    String stateValue = new String(stringWriter.getBuffer());
    

    // Write it to the old response writer
    oldRW.write(stateValue);
    
    // Now extract the parameter value from the buffer:
    stateValue = extractViewStateParamValue(stateValue);
    
    if (stateValue != null) 
    {
      Map<String, Object> m = context.getExternalContext().getRequestMap();
      m.put(BridgeImpl.UPDATED_VIEW_STATE_PARAM, stateValue);
    }
    
  }
  
  public StateManager getWrapped()
  {
    return mDelegatee;
  }
  
  private String extractViewStateParamValue(String buf)
  {
    // Locate the VIEW_STATE_PARAM field
    int i = buf.indexOf(ResponseStateManager.VIEW_STATE_PARAM);
    if (i < 0) return null;
    
    // now locate the end of the element so don't read beyond it.
    int end = buf.indexOf("/>", i);
    if (end < 0) return null;
    
    // now locate the value attribute
    int valStart = buf.indexOf("value", i);
    if (valStart < 0 || valStart > end) 
    {
      // must be earlier in the element
      buf = buf.substring(0, end);
      end = buf.length() - 1;
      i = buf.lastIndexOf("<");
      if (i < 0) return null;
      valStart = buf.indexOf("value", i);
      if (valStart < 0) return null;
    }
    
    // now extract the value between the quotes
    valStart = buf.indexOf('"', valStart);
    if (valStart < 0) return null;
    int valEnd = buf.indexOf('"', valStart + 1);
    if (valEnd < 0 || valEnd > end) return null;
    return buf.substring(valStart + 1, valEnd);
  }
}
