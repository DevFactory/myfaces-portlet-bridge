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
package org.apache.myfaces.portlet.faces.el;

import java.beans.FeatureDescriptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;
import javax.el.PropertyNotFoundException;
import javax.el.PropertyNotWritableException;

import javax.faces.context.FacesContext;
import javax.faces.context.ExternalContext;

import javax.portlet.PortletConfig;
import javax.portlet.PortletRequest;

import javax.portlet.faces.Bridge;
import javax.portlet.faces.BridgeUtil;

public class PortletELResolver extends ELResolver
{

  // Important preserve index (order) between array and constants
  public static final String[] IMPLICIT_OBJECTS          = new String[] { "portletConfig",
      "sessionApplicationScope", "sessionPortletScope", "portletPreferenceValue",
      "portletPreferenceValues"                         };

  public static final int      PORTLET_CONFIG            = 0;
  public static final int      SESSION_APPLICATION_SCOPE = 1;
  public static final int      SESSION_PORTLET_SCOPE     = 2;
  public static final int      PORTLET_PREFERENCE_VALUE  = 3;
  public static final int      PORTLET_PREFERENCE_VALUES = 4;

  public PortletELResolver()
  {
  }

  @Override
  public Object getValue(ELContext context, Object base, Object property) throws ELException
  {
    // variable resolution is a special case of property resolution
    // where the base is null.
    if (base != null)
    {
      return null;
    }
    if (property == null)
    {
      throw new PropertyNotFoundException("Null property");
    }

    FacesContext facesContext = (FacesContext) context.getContext(FacesContext.class);
    ExternalContext extCtx = facesContext.getExternalContext();

    // only process if running in a portlet request
    if (!BridgeUtil.isPortletRequest())
    {
      return null;
    }

    int index = Arrays.binarySearch(IMPLICIT_OBJECTS, property);
    if (index < 0)
    {
      return null;
    }
    else
    {
      switch (index)
      {
        case PORTLET_CONFIG:
          context.setPropertyResolved(true);
          return context.getContext(PortletConfig.class);
        case SESSION_APPLICATION_SCOPE:
          context.setPropertyResolved(true);
          return extCtx.getSessionMap().get(Bridge.APPLICATION_SCOPE_MAP);
        case SESSION_PORTLET_SCOPE:
          context.setPropertyResolved(true);
          return extCtx.getSessionMap();
        case PORTLET_PREFERENCE_VALUE:
          context.setPropertyResolved(true);
          return getPreferencesValueMap(extCtx);
        case PORTLET_PREFERENCE_VALUES:
          context.setPropertyResolved(true);
          return ((PortletRequest) extCtx.getRequest()).getPreferences().getMap();
        default:
          return null;
      }
    }
  }

  @Override
  public void setValue(ELContext context, Object base, Object property, Object val)
                                                                                   throws ELException
  {
    if (base != null)
    {
      return;
    }
    if (property == null)
    {
      throw new PropertyNotFoundException("Null property");
    }

    int index = Arrays.binarySearch(IMPLICIT_OBJECTS, property);
    if (index >= 0)
    {
      throw new PropertyNotWritableException((String) property);
    }
  }

  @Override
  public boolean isReadOnly(ELContext context, Object base, Object property) throws ELException
  {
    if (base != null)
    {
      return false;
    }
    if (property == null)
    {
      throw new PropertyNotFoundException("Null property");
    }

    int index = Arrays.binarySearch(IMPLICIT_OBJECTS, property);
    if (index >= 0)
    {
      context.setPropertyResolved(true);
      return true;
    }
    return false;
  }

  @Override
  public Class<?> getType(ELContext context, Object base, Object property) throws ELException
  {
    if (base != null)
    {
      return null;
    }
    if (property == null)
    {
      throw new PropertyNotFoundException("Null property");
    }

    int index = Arrays.binarySearch(IMPLICIT_OBJECTS, property);
    if (index >= 0)
    {
      context.setPropertyResolved(true);
    }
    return null;
  }

  @Override
  public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base)
  {
    if (base != null)
    {
      return null;
    }
    ArrayList<FeatureDescriptor> list = new ArrayList<FeatureDescriptor>(14);
    list.add(getFeatureDescriptor("portletConfig", "portletConfig", "portletConfig", false, false,
                                  true, Object.class, Boolean.TRUE));
    list.add(getFeatureDescriptor("sessionApplicationScope", "sessionApplicationScope",
                                  "sessionApplicationScope", false, false, true, Map.class,
                                  Boolean.TRUE));
    list.add(getFeatureDescriptor("sessionPortletScope", "sessionPortletScope",
                                  "sessionPortletScope", false, false, true, Map.class,
                                  Boolean.TRUE));
    list.add(getFeatureDescriptor("portletPreferenceValue", "portletPreferenceValue",
                                  "portletPreferenceValue", false, false, true, Map.class,
                                  Boolean.TRUE));
    list.add(getFeatureDescriptor("portletPreferenceValues", "portletPreferenceValues",
                                  "portletPreferenceValues", false, false, true, Map.class,
                                  Boolean.TRUE));
    return list.iterator();

  }

  @Override
  public Class<?> getCommonPropertyType(ELContext context, Object base)
  {
    if (base != null)
    {
      return null;
    }
    return String.class;
  }

  private FeatureDescriptor getFeatureDescriptor(String name, String displayName, String desc,
                                                 boolean expert, boolean hidden, boolean preferred,
                                                 Object type, Boolean designTime)
  {

    FeatureDescriptor fd = new FeatureDescriptor();
    fd.setName(name);
    fd.setDisplayName(displayName);
    fd.setShortDescription(desc);
    fd.setExpert(expert);
    fd.setHidden(hidden);
    fd.setPreferred(preferred);
    fd.setValue(ELResolver.TYPE, type);
    fd.setValue(ELResolver.RESOLVABLE_AT_DESIGN_TIME, designTime);
    return fd;
  }

  private Map getPreferencesValueMap(ExternalContext extCtx)
  {
    PortletRequest portletRequest = (PortletRequest) extCtx.getRequest();
    Enumeration e = portletRequest.getPreferences().getNames();
    Map m = null;

    while (e.hasMoreElements())
    {
      if (m == null)
      {
        m = new HashMap();
      }
      String name = (String) e.nextElement();
      String value = portletRequest.getPreferences().getValue(name, null);
      if (value != null)
      {
        m.put(name, value);
      }
    }
    return m;
  }

}
