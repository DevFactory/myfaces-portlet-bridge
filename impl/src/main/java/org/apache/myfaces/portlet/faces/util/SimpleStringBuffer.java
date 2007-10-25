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

/**
 * A non synchronized StringBuffer object that mimmicks the functionality of java.lang.StringBuffer.
 */
public final class SimpleStringBuffer
{
  private char mValue[];
  private int  mLength;

  public SimpleStringBuffer()
  {
    this(16);
  }

  public SimpleStringBuffer(int length)
  {
    mValue = new char[length];
  }

  public SimpleStringBuffer(String str)
  {
    this(str.length() + 16);
    append(str);
  }

  /**
   * Returns the length (character count) of this string buffer.
   * 
   * @return the number of characters in this string buffer.
   */
  public int length()
  {
    return mLength;
  }

  /**
   * Returns the current capacity of the String buffer. The capacity is the amount of storage
   * available for newly inserted characters; beyond which an allocation will occur.
   * 
   * @return the current capacity of this string buffer.
   */
  public int capacity()
  {
    return mValue.length;
  }

  /**
   * Ensures that the capacity of the buffer is at least equal to the specified minimum. If the
   * current capacity of this string buffer is less than the argument, then a new internal buffer is
   * allocated with greater capacity. The new capacity is the larger of:
   * <ul>
   * <li>The <code>minimumCapacity</code> argument.
   * <li>Twice the old capacity, plus <code>2</code>.
   * </ul>
   * If the <code>minimumCapacity</code> argument is nonpositive, this method takes no action and
   * simply returns.
   * 
   * @param minimumCapacity
   *          the minimum desired capacity.
   */
  public void ensureCapacity(int minimumCapacity)
  {
    if (minimumCapacity > mValue.length)
    {
      int newCapacity = (mValue.length + 1) * 2;
      if (minimumCapacity > newCapacity)
      {
        newCapacity = minimumCapacity;
      }

      char newValue[] = new char[newCapacity];
      System.arraycopy(mValue, 0, newValue, 0, mLength);
      mValue = newValue;

      // Debug Facility that dumps the stack trace when
      // the buffer grows allowing sizing to be tweaked!
      // SHOULD BE COMMENTED OUT
      // printStackTrace(new Throwable());
    }
  }

  public void setLength(int newLength)
  {
    if (newLength < 0)
    {
      throw new StringIndexOutOfBoundsException(newLength);
    }

    if (newLength > mValue.length)
    {
      ensureCapacity(newLength);
    }

    if (mLength < newLength)
    {
      for (; mLength < newLength; mLength++)
      {
        mValue[mLength] = '\0';
      }
    }
    else
    {
      mLength = newLength;
    }
  }

  public char charAt(int index)
  {
    if (index < 0 || index >= mLength)
    {
      throw new StringIndexOutOfBoundsException(index);
    }
    return mValue[index];
  }

  public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin)
  {
    if (srcBegin < 0 || srcBegin >= mLength)
    {
      throw new StringIndexOutOfBoundsException(srcBegin);
    }

    if (srcEnd < 0 || srcEnd > mLength)
    {
      throw new StringIndexOutOfBoundsException(srcEnd);
    }

    if (srcBegin < srcEnd)
    {
      System.arraycopy(mValue, srcBegin, dst, dstBegin, srcEnd - srcBegin);
    }
  }

  public void setCharAt(int index, char ch)
  {
    if (index < 0 || index >= mLength)
    {
      throw new StringIndexOutOfBoundsException(index);
    }
    mValue[index] = ch;
  }

  public SimpleStringBuffer append(Object obj)
  {
    return append(String.valueOf(obj));
  }

  public SimpleStringBuffer append(String str)
  {
    if (str == null)
    {
      str = String.valueOf(str);
    }

    int len = str.length();
    int newcount = mLength + len;
    if (newcount > mValue.length)
    {
      ensureCapacity(newcount);
    }
    str.getChars(0, len, mValue, mLength);
    mLength = newcount;
    return this;
  }

  public SimpleStringBuffer append(char[] str)
  {
    int len = str.length;
    int newcount = mLength + len;
    if (newcount > mValue.length)
    {
      ensureCapacity(newcount);
    }
    System.arraycopy(str, 0, mValue, mLength, len);
    mLength = newcount;
    return this;
  }

  public SimpleStringBuffer append(char[] str, int offset, int len)
  {
    int newcount = mLength + len;
    if (newcount > mValue.length)
    {
      ensureCapacity(newcount);
    }
    System.arraycopy(str, offset, mValue, mLength, len);
    mLength = newcount;
    return this;
  }

  public SimpleStringBuffer append(boolean b)
  {
    return append(String.valueOf(b));
  }

  public SimpleStringBuffer append(char c)
  {
    int newcount = mLength + 1;
    if (newcount > mValue.length)
    {
      ensureCapacity(newcount);
    }
    mValue[mLength++] = c;
    return this;
  }

  /**
   */
  public SimpleStringBuffer append(int i)
  {
    return append(String.valueOf(i));
  }

  /**
   */
  public SimpleStringBuffer append(long l)
  {
    return append(String.valueOf(l));
  }

  /**
   */
  public SimpleStringBuffer append(float f)
  {
    return append(String.valueOf(f));
  }

  /**
   */
  public SimpleStringBuffer append(double d)
  {
    return append(String.valueOf(d));
  }

  /**
   */
  public SimpleStringBuffer insert(int offset, Object obj)
  {
    return insert(offset, String.valueOf(obj));
  }

  /**
   */
  public SimpleStringBuffer insert(int offset, String str)
  {
    if (offset < 0 || offset > mLength)
    {
      throw new StringIndexOutOfBoundsException();
    }
    int len = str.length();
    int newcount = mLength + len;
    if (newcount > mValue.length)
    {
      ensureCapacity(newcount);
    }
    System.arraycopy(mValue, offset, mValue, offset + len, mLength - offset);
    str.getChars(0, len, mValue, offset);
    mLength = newcount;
    return this;
  }

  /**
   */
  public SimpleStringBuffer insert(int offset, char[] str)
  {
    if (offset < 0 || offset > mLength)
    {
      throw new StringIndexOutOfBoundsException();
    }
    int len = str.length;
    int newcount = mLength + len;
    if (newcount > mValue.length)
    {
      ensureCapacity(newcount);
    }
    System.arraycopy(mValue, offset, mValue, offset + len, mLength - offset);
    System.arraycopy(str, 0, mValue, offset, len);
    mLength = newcount;
    return this;
  }

  /**
   */
  public SimpleStringBuffer insert(int offset, boolean b)
  {
    return insert(offset, String.valueOf(b));
  }

  /**
   */
  public SimpleStringBuffer insert(int offset, char c)
  {
    int newcount = mLength + 1;
    if (newcount > mValue.length)
    {
      ensureCapacity(newcount);
    }
    System.arraycopy(mValue, offset, mValue, offset + 1, mLength - offset);
    mValue[offset] = c;
    mLength = newcount;
    return this;
  }

  /**
   */
  public SimpleStringBuffer insert(int offset, int i)
  {
    return insert(offset, String.valueOf(i));
  }

  /**
   */
  public SimpleStringBuffer insert(int offset, long l)
  {
    return insert(offset, String.valueOf(l));
  }

  /**
   */
  public SimpleStringBuffer insert(int offset, float f)
  {
    return insert(offset, String.valueOf(f));
  }

  /**
   */
  public SimpleStringBuffer insert(int offset, double d)
  {
    return insert(offset, String.valueOf(d));
  }

  /**
   * Removes the characters in a substring of this <code>
   * SimpleStringBuffer</code>. The substring
   * begins at the specified <code>start</code> and extends to the character at index
   * <code>end</code> - 1 or to the end of the SimpleStringBuffer if no such character exists. If
   * start is equal to end, no changes are made.
   * 
   * @param start -
   *          the beginning index, inclusive
   * @param end -
   *          the ending index, exclusive
   * 
   * @return This simple string buffer
   * @exception StringIndexOutOfBoundsException -
   *              if start is negative, greater than <code>length()</code>, or greater than
   *              <code>end</code>.
   * 
   */
  public SimpleStringBuffer delete(int start, int end)
  {
    if (start < 0)
    {
      throw new StringIndexOutOfBoundsException(start);
    }
    if (end > mLength)
    {
      end = mLength;
    }
    if (start > end)
    {
      throw new StringIndexOutOfBoundsException();
    }
    int numChars = end - start;
    if (numChars > 0)
    {
      System.arraycopy(mValue, start + numChars, mValue, start, mLength - end);
      mLength -= numChars;
    }
    return this;
  }

  /**
   * Removes the character at the specified position in this <code>SimpleStringBuffer</code>
   * (shortening the <code>SimpleStringBuffer</code> by one character).
   * 
   * @param index -
   *          index of the character to remove
   * 
   * @return This simple string buffer
   * @exception StringIndexOutOfBoundsException -
   *              if the <code>index</code> is negative or greater than or equal to
   *              <code>length()</code>.
   * 
   */
  public SimpleStringBuffer deleteCharAt(int index)
  {
    if (index < 0 || index >= mLength)
    {
      throw new StringIndexOutOfBoundsException();
    }
    System.arraycopy(mValue, index + 1, mValue, index, mLength - index - 1);
    mLength--;
    return this;
  }

  /**
   * Replaces the characters in a substring of this <code>SimpleStringBuffer</code> with
   * characters in the specified String. The substring begins at the specified start and extends to
   * the character at index <code>end</code> - 1 or to the end of the
   * <code>SimpleStringBuffer</code> if no such character exists. First the characters in the
   * substring are removed and then the specified String is inserted at start. (The
   * <code>SimpleStringBuffer</code> will be lengthened to accommodate the specified String if
   * necessary.)
   * 
   * @param start -
   *          the beginning index, inclusive
   * @param end -
   *          the ending index, exclusive
   * @param str -
   *          string that will replace previous contents
   * 
   * @return This simple string buffer
   * @exception StringIndexOutOfBoundsException -
   *              if start is negative, greater than <code>length()</code>, or greater than
   *              <code>end</code>.
   * 
   */
  public SimpleStringBuffer replace(int start, int end, String str)
  {
    if (start < 0)
    {
      throw new StringIndexOutOfBoundsException(start);
    }
    if (end > mLength)
    {
      end = mLength;
    }
    if (start > end)
    {
      throw new StringIndexOutOfBoundsException();
    }

    int numChars = str.length();
    int newLength = mLength + numChars - (end - start);

    if (newLength > mValue.length)
    {
      ensureCapacity(newLength);
    }
    System.arraycopy(mValue, end, mValue, start + numChars, mLength - end);

    str.getChars(0, numChars, mValue, start);
    mLength = newLength;

    return this;
  }

  /**
   * Returns a new <code>String</code> that contains a subsequence of characters currently
   * contained in this <code>SimpleStringBuffer</code>.The substring begins at the specified
   * index and extends to the end of the <code>StringBuffer</code>.
   * 
   * @param start
   *          The beginning index, inclusive.
   * @return The new string.
   * @exception StringIndexOutOfBoundsException
   *              if <code>start</code> is less than zero, or greater than the length of this
   *              <code>StringBuffer</code>.
   */
  public String substring(int start)
  {
    return substring(start, mLength);
  }

  /**
   * Returns a new <code>String</code> that contains a subsequence of characters currently
   * contained in this <code>SimpleStringBuffer</code>. The substring begins at the specified
   * <code>start</code> and extends to the character at index <code>end -
   * 1</code>.
   * 
   * @param start
   *          The beginning index, inclusive.
   * @param end
   *          The ending index, exclusive.
   * @return The new string.
   * @exception StringIndexOutOfBoundsException
   *              if <code>start</code> or <code>end</code> are negative or greater than
   *              <code>length()</code>, or <code>start</code> is greater than <code>end</code>.
   */
  public String substring(int start, int end)
  {
    if (start < 0)
    {
      throw new StringIndexOutOfBoundsException(start);
    }
    if (end > mLength)
    {
      throw new StringIndexOutOfBoundsException(end);
    }
    if (start > end)
    {
      throw new StringIndexOutOfBoundsException(end - start);
    }
    return new String(mValue, start, end - start);
  }

  /**
   */
  @Override
  public String toString()
  {
    return new String(getValue(), 0, length());
  }

  public char[] getValue()
  {
    return mValue;
  }
}
