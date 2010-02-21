/*
 * Digital Audio Access Protocol (DAAP)
 * Copyright (C) 2004-2010 Roger Kapsi
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

package org.ardverk.daap.nio;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.ardverk.daap.DaapAudioResponse;
import org.ardverk.daap.DaapConfig;
import org.ardverk.daap.DaapRequest;
import org.ardverk.daap.DaapStreamException;
import org.ardverk.daap.Song;

/**
 * DaapAudioResponse.
 * 
 * @author Roger Kapsi
 */
public class DaapAudioResponseNIO extends DaapAudioResponse {

    private ByteBuffer headerBuffer;
    private FileChannel fileChannel;
    private DaapConnectionNIO connection;

    public DaapAudioResponseNIO(DaapRequest request, Song song, File file,
            long pos, long end) throws IOException {
        this(request, song, new FileInputStream(file), pos, end);
    }

    /** Creates a new instance of DaapAudioResponse */
    public DaapAudioResponseNIO(DaapRequest request, Song song,
            FileInputStream in, long pos, long end) {
        super(request, song, in, pos, end);

        headerBuffer = ByteBuffer.wrap(header);
        this.connection = (DaapConnectionNIO) request.getConnection();

        fileChannel = in.getChannel();
    }

    public boolean hasRemaining() {
        if (headerBuffer.hasRemaining())
            return true;
        else
            return (pos < end);
    }

    public boolean write() throws IOException {

        if (headerBuffer.hasRemaining()) {

            try {

                connection.getWriteChannel().write(headerBuffer);

                if (headerBuffer.hasRemaining() == true) {
                    return false;
                }

            } catch (IOException err) {
                close();
                throw err;
            }
        }

        try {
            return stream();
        } catch (IOException err) {
            throw new DaapStreamException(err);
        }
    }

    private boolean stream() throws IOException {

        if (pos < end) {

            if (!connection.getWriteChannel().isOpen()) {
                close();
                return true;

            } else {

                // Stream...
                try {
                    DaapConfig config = request.getServer().getConfig();
                    pos += fileChannel.transferTo(pos, config.getBufferSize(),
                            connection.getWriteChannel());

                    if (pos >= end) {
                        close();
                        return true;
                    } else {
                        return false;
                    }

                } catch (IOException err) {
                    close();
                    throw err;
                }

            }

        } else {
            return true;
        }
    }

    protected void close() throws IOException {
        super.close();
        fileChannel.close();
    }
}