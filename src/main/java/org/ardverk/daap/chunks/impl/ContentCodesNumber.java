/* 
 * Digital Audio Access Protocol (DAAP)
 * Copyright (C) 2004 Roger Kapsi, info at kapsi dot de
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.ardverk.daap.chunks.impl;

import org.ardverk.daap.DaapUtil;
import org.ardverk.daap.chunks.UIntChunk;

/**
 * The number of a ContentCode (it's actually a four character code). 
 * It is needed to build a list of capabilities of the Server which is 
 * send to the client...
 *
 * @author  Roger Kapsi
 */
public class ContentCodesNumber extends UIntChunk {
    
    public ContentCodesNumber() {
        this(0);
    }
    
    public ContentCodesNumber(long number) {
        super("mcnm", "dmap.contentcodesnumber", number);
    }
    
    public String getValueContentCode() {
        return DaapUtil.toContentCodeString(getValue());
    }
    
    public String toString(int indent) {
        return indent(indent) + name + "(" + contentCode + "; int)=" + getValueContentCode();
    }
}
