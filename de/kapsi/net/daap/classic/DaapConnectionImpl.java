
package de.kapsi.net.daap.classic;

import java.io.*;
import java.net.*;
import java.util.*;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpParser;
import org.apache.commons.httpclient.HttpStatus;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import de.kapsi.net.daap.DaapServer;
import de.kapsi.net.daap.DaapRequest;
import de.kapsi.net.daap.DaapResponse;
import de.kapsi.net.daap.DaapConnection;
import de.kapsi.net.daap.DaapSession;
import de.kapsi.net.daap.DaapResponseFactory;
import de.kapsi.net.daap.DaapRequestProcessor;
import de.kapsi.net.daap.DaapResponseWriter;

/**
 * This class is a cover for an incoming connection. An connection
 * can either be a general DAAP connection or an Audio request.
 */
public class DaapConnectionImpl implements DaapConnection, Runnable {
    
    private static final Log LOG = LogFactory.getLog(DaapConnectionImpl.class);
    
    private static final int UNDEF  = 0;
    private static final int NORMAL = 1;
    private static final int AUDIO  = 2;
    
    private DaapServerImpl server;
    
    private Socket socket;
    
    private InputStream in;
    private OutputStream out;
    //private ResponseWriter writer;
    
    //private int keepAlive = 0;
    
    private DaapSession session;
    //private boolean audioStream;
    
    //private DaapRequest request;
    
    private DaapResponseWriter writer;
    private DaapRequestProcessor processor;
    
    private int type = UNDEF;
    
    public DaapConnectionImpl(DaapServerImpl server, Socket socket) throws IOException {
        
        this.server = server;
        this.socket = socket;
        
        DaapResponseFactory factory = new DaapResponseFactoryImpl(this);
        processor = new DaapRequestProcessor(this, factory);
        
        writer = new DaapResponseWriter();
        
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }
    
    public DaapServer getServer() {
        return server;
    }
    
    
    public boolean isUndef() {
        return (type == UNDEF);
    }
    
    /**
     * Returns true if this connection is an Audio stream
     */
    public boolean isAudioStream() {
        return (type == AUDIO);
    }
    
    public boolean isNormal() {
        return (type == NORMAL);
    }
    
    private boolean read() throws IOException {
        
        if (isAudioStream()) {
            throw new IOException("Illegal operation");
        }
        
        DaapRequest request = readRequest();
        
        if (isUndef()) {
            type = (request.isSongRequest()) ? AUDIO : NORMAL;
            
            if (!request.isSongRequest() &&
                !request.isServerInfoRequest()) {

                // disconnect as the first request must be
                // either a song or server-info request!
                throw new IOException("Illegal request: " + request);
            }
            
            // AudioStreams have a session-id and we must check the id
            if (isAudioStream()) {
                Integer sid = new Integer(request.getSessionId());
                if (server.isSessionIdValid(sid) == false) {
                    throw new IOException("Unknown Session-ID: " + sid);
                }
            }
            
            // add connection to the connection pool
            if ( ! server.addConnection(this) ) {
                throw new IOException("Server refused this connection");
            }
        }
        
        DaapResponse response = processor.process(request);
        if (response != null) {
            writer.add(response);
        }
        
        return true;
    }
    
    private boolean write() throws IOException {
        
        if (writer.write()) {
            
            if (isAudioStream()) {
                return false;
            }
        }
        
        return true;
    }
    
    public void run() {
        
        try {
            
            do {
                read();
            } while(write());
            
        } catch (SocketException err) {
            //LOG.info(err);
            
            // This exception can be ignored as it's thrown
            // whenever the user disconnects...
            
        } catch (IOException err) {
            LOG.error(err);
            
        } finally {
            close();
        }
    }
    
    public synchronized void update() throws IOException {
        
        if (isNormal()) {
            DaapSession session = getSession(false);

            // Do not trigger new updates if an update for this connection
            // is already running, it will autumatically update to the
            // lates revision of the library!

            if (session != null && !session.hasAttribute("UPDATE_LOCK")) {

                Integer sessionId = session.getSessionId();
                Integer delta = (Integer)session.getAttribute("DELTA");
                Integer revisionNumber 
                    = (Integer)session.getAttribute("REVISION-NUMBER");

                if (delta != null && revisionNumber != null) {

                    DaapRequest request =
                        new DaapRequest(sessionId.intValue(),
                            revisionNumber.intValue(), delta.intValue());

                    DaapResponse response = processor.process(request);
                    writer.add(response);
                }
            }
        }
    }
    
    public void close() {
        
        if (session != null)
            session.invalidate();
        
        try {
            if (in != null)
                in.close();
        } catch (IOException err) {
            LOG.error("Error while closing connection", err);
        }
        
        try {
            if (out != null)
                out.close();
        } catch (IOException err) {
            LOG.error("Error while closing connection", err);
        }
        
        /*try {
            if (writer != null)
                writer.close();
        } catch (IOException err) {
            LOG.error("Error while closing connection", err);
        }*/
        
        try {
            if (socket != null)
                socket.close();
        } catch (IOException err) {
            LOG.error("Error while closing connection", err);
        }
        
        server.removeConnection(this);
    }
    
    public DaapSession getSession(boolean create) {
        
        if (session == null && create) {
            Integer sessionId = server.createSessionId();
            session = new DaapSession(sessionId);
        }
        
        return session;
    }
    
    public InputStream getInputStream() {
        return in;
    }
    
    public OutputStream getOutputStream() {
        return out;
    }
    
    private DaapRequest readRequest() throws IOException {
        
        String line = null;
        
        do {
            line = HttpParser.readLine(in);
        } while(line != null && line.length() == 0);
        
        if(line == null) {
            throw new IOException("Request is null");
        }
        
        DaapRequest request = new DaapRequest(line);
        Header[] headers = HttpParser.parseHeaders(in);
        request.addHeaders(headers);
        request.setConfig(server.getConfig());
        
        return request;
    }
    
    public String toString() {
        StringBuffer buffer = new StringBuffer("DaapConnection [");
        
        buffer.append("Host: ").append(socket.getInetAddress()).append(":")
        .append(socket.getPort());
        
        buffer.append(", audioStream: ").append(isAudioStream());
        buffer.append(", hasSession: ").append(getSession(false)!=null);
        
        return buffer.append("]").toString();
    }
}
