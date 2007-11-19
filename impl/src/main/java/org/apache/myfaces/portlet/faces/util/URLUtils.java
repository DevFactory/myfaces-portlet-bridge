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

package org.apache.myfaces.portlet.faces.util;

public class URLUtils
{

  /**
   * Borrowed from package oracle.adfinternal.view.faces.share.url.EncoderUtils
   */
  public static String appendURLArguments(StringBuffer buffer, String baseURL,
                                          String[] keysAndValues)
  {

    // Bug 1814825: the anchor has to stay on the end.
    int anchorIndex = baseURL.indexOf('#');

    if (anchorIndex >= 0)
    {
      buffer.append(baseURL.substring(0, anchorIndex));
    }
    else
    {
      buffer.append(baseURL);
    }

    boolean queryAppended = baseURL.indexOf('?') >= 0;

    for (int i = 0; i < keysAndValues.length; i += 2)
    {
      String value = keysAndValues[i + 1];
      if (value != null)
      {
        // only append '?' at start if the URL doesn't already contain
        // arguments
        if (!queryAppended)
        {
          queryAppended = true;
          buffer.append('?');
        }
        else
        {
          buffer.append('&');
        }

        buffer.append(keysAndValues[i]);
        buffer.append('=');
        buffer.append(value);
      }
    }

    String beforeEncode = buffer.toString();
    return beforeEncode;
  }

  /**
   * Borrowed from package oracle.adfinternal.view.faces.share.url.EncoderUtils
   */
  public static String appendURLArguments(String baseURL, String[] keysAndValues)
  {
    // buffer length = base + separators + keys + values
    int bufferLength = baseURL.length() + keysAndValues.length;
    for (int i = 0; i < keysAndValues.length; i += 2)
    {
      String value = keysAndValues[i + 1];
      if (value != null)
      {
        bufferLength += keysAndValues[i].length() + value.length();
      }
    }

    StringBuffer buffer = new StringBuffer(bufferLength);

    return appendURLArguments(buffer, baseURL, keysAndValues);
  }

  public static String convertFromRelative(String currentPath, String relativeLoc)
                                                                                  throws IllegalArgumentException
  {
    // determine if and how many levels we must walk up the currentPath
    int levels = 0;
    int i = 0, length = relativeLoc.length();
    while (i + 1 < length)
    {
      if (relativeLoc.charAt(i) != '.')
      {
        break;
      }
      else if (relativeLoc.charAt(i) == '.' && relativeLoc.charAt(i + 1) == '/')
      {
        // no new level but prune the ./
        i += 2;
      }
      else if (i + 2 < length && relativeLoc.charAt(i) == '.' && relativeLoc.charAt(i + 1) == '.'
               && relativeLoc.charAt(i + 2) == '/')
      {
        levels += 1;
        i += 3;
      }
    }

    StringBuffer sb = new StringBuffer(currentPath);
    if (currentPath.endsWith("/"))
    {
      sb = sb.deleteCharAt(currentPath.length() - 1);
    }
    for (int j = 0; j < levels; j++)
    {
      int loc = sb.lastIndexOf("/");
      if (loc < 0)
      {
        throw new IllegalArgumentException("Location: " + relativeLoc
                                           + "Can't be made relative to: " + currentPath);
      }
      sb = sb.delete(loc, sb.length());
    }

    // now sb should contain root path without trailing / so add one
    sb = sb.append("/");

    // now add the portion of the relativeLoc that doesn't contain
    // the relative references
    sb = sb.append(relativeLoc.substring(i));

    return sb.toString();

  }

}
