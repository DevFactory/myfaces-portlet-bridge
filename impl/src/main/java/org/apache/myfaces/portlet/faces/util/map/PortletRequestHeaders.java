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

package org.apache.myfaces.portlet.faces.util.map;

import javax.portlet.PortletRequest;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;

import javax.portlet.ActionRequest;
import javax.portlet.faces.Bridge;

public class PortletRequestHeaders
{
  private PortletRequest mPortletRequest = null;
  private List           mHeaderNames    = null;
  private Map            mHeaders        = null;

  public PortletRequestHeaders(PortletRequest request)
  {
    mPortletRequest = request;
  }

  public java.lang.String getHeader(java.lang.String name)
  {
    // initialize the header map if it hasn't been already
    initHeaderMap();

    List headerVals = (List) mHeaders.get(name.toUpperCase());
    return headerVals == null ? null : (String) headerVals.get(0);
  }

  public java.util.Enumeration getHeaders(java.lang.String name)
  {
    // initialize the header map if it hasn't been already
    initHeaderMap();

    List headerVals = (List) mHeaders.get(name.toUpperCase());
    return Collections.enumeration(headerVals == null ? Collections.EMPTY_LIST : headerVals);
  }

  public java.util.Enumeration getHeaderNames()
  {
    // initialize the header map if it hasn't been already
    initHeaderMap();

    return Collections.enumeration(mHeaderNames);
  }

  /**
   * Does 'lazy' initialization of Map of 'properties', i.e. mime headers.
   */
  protected boolean initHeaderMap()
  {
    if (mHeaders != null)
    {
      return false;
    }

    mHeaders = Collections.EMPTY_MAP;
    mHeaderNames = Collections.EMPTY_LIST;

    Enumeration props = mPortletRequest.getPropertyNames();
    Enumeration values = null;
    StringBuffer property = null;

    while (props != null && props.hasMoreElements())
    {
      String name = (String) props.nextElement();
      values = mPortletRequest.getProperties(name);
      while (values != null && values.hasMoreElements())
      {
        addProperty(name, (String) values.nextElement());
      }
    }

    // if they don't already exist, now add in the the required (HTTP)
    // headers to ensure compatibility with servlets
    if (!containsHeader(mHeaderNames, "ACCEPT"))
    {
      values = mPortletRequest.getResponseContentTypes();
      if (property == null)
      {
        property = new StringBuffer(64);
      }
      else
      {
        property.setLength(0);
      }
      boolean addComma = false;
      while (values != null && values.hasMoreElements())
      {
        String s = (String) values.nextElement();
        if (s != null)
        {
          if (addComma)
          {
            property = property.append(',');
          }
          else
          {
            addComma = true;
          }
          property = property.append(s);
        }
      }

      if (addComma)
      {
        addProperty("ACCEPT", property.toString());
      }
    }

    if (!containsHeader(mHeaderNames, "ACCEPT-LANGUAGE"))
    {
      values = mPortletRequest.getLocales();
      if (property == null)
      {
        property = new StringBuffer(64);
      }
      else
      {
        property.setLength(0);
      }
      boolean addComma = false;
      while (values != null && values.hasMoreElements())
      {
        Locale l = (Locale) values.nextElement();
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

    if ((Bridge.PortletPhase) mPortletRequest.getAttribute(Bridge.PORTLET_LIFECYCLE_PHASE) == Bridge.PortletPhase.ActionPhase)
    {

      if (!containsHeader(mHeaderNames, "CONTENT-TYPE"))
      {
        String contentType = ((ActionRequest) mPortletRequest).getContentType();
        String charset = ((ActionRequest) mPortletRequest).getCharacterEncoding();

        if (contentType != null)
        {
          if (property == null)
          {
            property = new StringBuffer(64);
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

    return true;
  }

  private boolean containsHeader(List headerNames, String key)
  {
    Iterator i = headerNames.iterator();
    while (i.hasNext())
    {
      String name = (String) i.next();
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
      mHeaders = new HashMap(40);
      mHeaderNames = new ArrayList(30);
    }
    // Store against UPPER CASE key to make case-insensitive
    String upperName = name.toUpperCase();
    List propertyList = (List) mHeaders.get(upperName);
    if (propertyList == null)
    {
      propertyList = new ArrayList(4);
      mHeaders.put(upperName, propertyList);
      mHeaderNames.add(name);
    }
    propertyList.add(value);
  }
}
