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

import java.text.MessageFormat;
import java.util.ResourceBundle;

// TODO once we figure out which class is shipped with the 2.0 container
// we'll be using that class instead
// Borrowed from oracle.portlet.utils

public class TextUtils
{

  public static final String getString(ResourceBundle bundle, String key, Object[] args)
  {
    return formatMessage(bundle.getString(key), args);
  }

  public static final String getString(ResourceBundle bundle, String key)
  {
    return formatMessage(bundle.getString(key), null);
  }

  public static final String formatMessage(String message, Object[] args)
  {
    if (args != null)
    {
      return MessageFormat.format(message, args);
    }
    else
    {
      return message;
    }
  }

  /**
   * Provides the "global substring search and replace" functionality missing from the JDK.
   * 
   * @param orig
   *          the original string to process
   * @param search
   *          the substring to search for in <code>orig</code>
   * @param replace
   *          the string to replace all occurrences of <code>search</code> with
   * @return copy of <code>orig</code> with all occurrences of <code>search</code> replaced by
   *         <code>replace</code>
   */
  public static final String globalReplace(String orig, String search, String replace)
  {
    // OPTIMIZATION: Return original string if it doesn't contain the search
    // string
    int searchLen = search.length();
    if (searchLen == 0)
    {
      return orig;
    }
    int nextPos = orig.indexOf(search);
    if (nextPos == -1)
    {
      return orig;
    }
    int origLen = orig.length();
    int startPos = 0;
    SimpleStringBuffer result = new SimpleStringBuffer(origLen + 100);

    // Use 'do' loop, because we know the search string occurs at least once
    do
    {
      if (nextPos > startPos)
      {
        result.append(orig.substring(startPos, nextPos));
      }
      result.append(replace);
      startPos = nextPos + searchLen;
    } while (startPos < origLen && (nextPos = orig.indexOf(search, startPos)) != -1);

    if (startPos < origLen)
    {
      result.append(orig.substring(startPos));
    }

    return result.toString();
  }
}
