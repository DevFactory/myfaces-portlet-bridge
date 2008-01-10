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
 */

package org.apache.myfaces.portlet.faces.util.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.portlet.ActionRequest;
import javax.portlet.PortletRequest;
import javax.portlet.faces.Bridge;

public class PortletRequestHeaders
{
  private PortletRequest mPortletRequest = null;
  private List<String> mHeaderNames = null;
  private Map<String, List<String>> mHeaders = null;

  public PortletRequestHeaders(PortletRequest request)
  {
    mPortletRequest = request;
  }

  @SuppressWarnings("unchecked")
  public String getHeader(String name)
  {
    // initialize the header map if it hasn't been already
    initHeaderMap();

    List<String> headerVals = mHeaders.get(name.toUpperCase());
    return headerVals == null ? null : (String) headerVals.get(0);
  }

  public Enumeration<String> getHeaders(String name)
  {
    // initialize the header map if it hasn't been already
    initHeaderMap();

    List<String> headerVals = mHeaders.get(name.toUpperCase());
    
    if (headerVals == null)
    {
      headerVals = Collections.emptyList();
    }
    
    return Collections.enumeration(headerVals);
  }

  public Enumeration<String> getHeaderNames()
  {
    // initialize the header map if it hasn't been already
    initHeaderMap();

    return Collections.enumeration(mHeaderNames);
  }

  /**
   * Does 'lazy' initialization of Map of 'properties', i.e. mime headers.
   */
  @SuppressWarnings("unchecked")
  protected boolean initHeaderMap()
  {
    if (mHeaders != null)
    {
      return false;
    }

    mHeaders = Collections.emptyMap();
    mHeaderNames = Collections.emptyList();

    Enumeration<String> props = mPortletRequest.getPropertyNames();
    while (props.hasMoreElements())
    {
      String name = props.nextElement();
      Enumeration<String> values = mPortletRequest.getProperties(name);
      while (values != null && values.hasMoreElements())
      {
        addProperty(name, values.nextElement());
      }
    }
    
    StringBuilder property = null;

    // if they don't already exist, now add in the the required (HTTP)
    // headers to ensure compatibility with servlets
    if (!containsHeader(mHeaderNames, "ACCEPT"))
    {
      Enumeration<String> contentTypes = mPortletRequest.getResponseContentTypes();
      property = new StringBuilder(64);
      
      boolean addComma = false;
      while (contentTypes.hasMoreElements())
      {
        String type = contentTypes.nextElement();
        if (type != null)
        {
          if (addComma)
          {
            property = property.append(',');
          }
          else
          {
            addComma = true;
          }
          
          property = property.append(type);
        }
      }

      if (addComma)
      {
        addProperty("ACCEPT", property.toString());
      }
    }

    if (!containsHeader(mHeaderNames, "ACCEPT-LANGUAGE"))
    {
      Enumeration<Locale> locales = mPortletRequest.getLocales();
      if (property == null)
      {
        property = new StringBuilder(64);
      }
      else
      {
        property.setLength(0);
      }
      
      boolean addComma = false;
      while (locales.hasMoreElements())
      {
        Locale l = locales.nextElement();
        if (l != null)
        {
          if (addComma)
          {
            property = property.append(',');
          }
          else
          {
            addComma = true;
          }
          
          String s = l.getLanguage();
          // only add if language not empty
          if (s.length() > 0)
          {
            property = property.append(s);
            s = l.getCountry();
            if (s.length() > 0)
            {
              property = property.append('-');
              property = property.append(s);
            }
          }
        }
      }

      if (addComma)
      {
        addProperty("ACCEPT-LANGUAGE", property.toString());
      }
    }

    if ((Bridge.PortletPhase) mPortletRequest.getAttribute(Bridge.PORTLET_LIFECYCLE_PHASE) == Bridge.PortletPhase.ACTION_PHASE)
    {

      if (!containsHeader(mHeaderNames, "CONTENT-TYPE"))
      {
        String contentType = ((ActionRequest) mPortletRequest).getContentType();
        String charset = ((ActionRequest) mPortletRequest).getCharacterEncoding();

        if (contentType != null)
        {
          if (property == null)
          {
            property = new StringBuilder(64);
          }
          else
          {
            property.setLength(0);
          }

          property = property.append(contentType);
          if (charset != null)
          {
            property = property.append("; charset=");
            property = property.append(charset);
          }
          addProperty("CONTENT-TYPE", property.toString());
        }
      }

      if (!containsHeader(mHeaderNames, "CONTENT-LENGTH"))
      {
        int contentLength = ((ActionRequest) mPortletRequest).getContentLength();

        if (contentLength != -1)
        {
          addProperty("CONTENT-LENGTH", String.valueOf(contentLength));
        }
      }

    }
    // Technically don't need this test here but I will forget to change this code when
    // JSR 286 is supported and there are more phases.
    else if ((Bridge.PortletPhase) mPortletRequest.getAttribute(Bridge.PORTLET_LIFECYCLE_PHASE) == Bridge.PortletPhase.RENDER_PHASE)
    {
      // its the RENDER_PHASE -- spec says we must remove the CONTENT_TYPE if 
      // came in the request -- so it matches null return from
      // EC.getRequestContentType/CharacterSetEncoding
      mHeaders.remove("CONTENT-TYPE");
      mHeaderNames.remove("CONTENT-TYPE");
    }

    return true;
  }

  private boolean containsHeader(List<String> headerNames, String key)
  {
    for (String name : headerNames)
    {
      if (key.toUpperCase().equals(name.toUpperCase()))
      {
        return true;
      }
    }
    
    return false;
  }

  protected final void addProperty(String name, String value)
  {
    if (mHeaders == Collections.EMPTY_MAP)
    {
      mHeaders = new HashMap<String, List<String>>(40);
      mHeaderNames = new ArrayList<String>(30);
    }
    // Store against UPPER CASE key to make case-insensitive
    String upperName = name.toUpperCase();
    List<String> propertyList = mHeaders.get(upperName);
    if (propertyList == null)
    {
      propertyList = new ArrayList<String>(4);
      mHeaders.put(upperName, propertyList);
      mHeaderNames.add(name);
    }
    propertyList.add(value);
  }
}
