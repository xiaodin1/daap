
package de.kapsi.net.daap;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.NoSuchElementException;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.Header;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 */
public class DaapRequest {
    
    public static final int UNDEF_VALUE         = 0;	//  zero means not defined (system wide)
    
    public static final int SERVER_INFO         = 1;	//  "/server-info"
    public static final int CONTENT_CODES       = 2;	//  "/content-codes"
    public static final int LOGIN		= 3;	//  "/login"
    public static final int LOGOUT		= 4;	//  "/logout"
    public static final int UPDATE		= 5;	//  "/update"
    public static final int RESOLVE		= 6;	//  "/resolve"
    public static final int DATABASES		= 7;	//  "/databases"
    public static final int DATABASE_SONGS	= 8;	//  "/databases/databaseId/items"
    public static final int DATABASE_PLAYLISTS  = 9;	//  "/databases/databaseId/containers"
    public static final int PLAYLIST_SONGS	= 10;   //  "/databases/databaseId/containers/containerId/items"
    public static final int SONG		= 11;   //  "/databases/databaseId/items/itemId.format"
    
    private static final Log LOG = LogFactory.getLog(DaapRequest.class);
    
    private String method;
    private URI uri;
    private String protocol;
    
    private Map queryMap;
    
    private int sessionId = UNDEF_VALUE;
    private int revisionNumber = UNDEF_VALUE;
    private int delta = UNDEF_VALUE;
    
    private ArrayList meta;
    private String metaString;
    
    private int requestType = UNDEF_VALUE;
    private int databaseId = UNDEF_VALUE;
    private int containerId = UNDEF_VALUE;
    private int itemId = UNDEF_VALUE;
    
    private ArrayList headers;
    private boolean isServerSideRequest;
    private boolean isUpdateType;
   
    /**
     * 
     */
    private DaapRequest() {
        headers = new ArrayList();
    }
    
    /**
     * Creates a server side fake update request to issue an update
     */
    public DaapRequest(int sessionId, int revisionNumber, int delta) {
        this();
        
        this.sessionId = sessionId;
        this.revisionNumber = revisionNumber;
        this.delta = delta;
        
        this.requestType = UPDATE;
        this.isServerSideRequest = true;
        this.isUpdateType = false;
    }
    
    /**
     * Creates a DAAP request from the the requestLine
     */
    public DaapRequest(String requestLine) throws URIException {
        this();
        
        String method = null;
        URI uri = null;
        String protocol = null;
        
        try {
            StringTokenizer st = new StringTokenizer(requestLine, " ");
            method = st.nextToken();
            
            try {
                uri = new URI(st.nextToken().toCharArray());
            } catch (URIException err) {
                if (LOG.isErrorEnabled()) {
                    LOG.error(err);
                }
            }
            
            protocol = st.nextToken();
        } catch (NoSuchElementException err) {
            if (LOG.isErrorEnabled()) {
                LOG.error(err);
            }
        }
        
        this.isServerSideRequest = false;
        this.isUpdateType = false;
        
        setMethod(method);
        setURI(uri);
        setProtocol(protocol); 
    }
    
    /**
     *
     */
    public DaapRequest(String method, URI uri, String protocol) throws URIException {
        this();
        
        this.isServerSideRequest = false;
        this.isUpdateType = false;
        
        setMethod(method);
        setURI(uri);
        setProtocol(protocol); 
    }
    
    /**
     *
     */
    public void setMethod(String method) {
        this.method = method;
    }
    
    /**
     *
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
    
    public void setURI(URI uri) throws URIException {
        
        this.uri = uri;
        
        if (uri != null) {
            
            String path = uri.getPath();

            this.queryMap = DaapUtil.parseQuery(uri.getQuery());

            if (path.equals("/server-info")) {
                requestType = SERVER_INFO;
            } else if (path.equals("/content-codes")) {
                requestType = CONTENT_CODES;
            } else if (path.equals("/login")) {
                requestType = LOGIN;
            } else if (path.equals("/logout")) {
                requestType = LOGOUT;
            } else if (path.equals("/update")) {
                requestType = UPDATE;
            } else if (path.equals("/resolve")) {
                requestType = RESOLVE;
            }

            if (queryMap.containsKey("session-id")) {
                sessionId = Integer.parseInt((String)queryMap.get("session-id"));
            }

            if (sessionId != UNDEF_VALUE) {

                if (queryMap.containsKey("revision-number")) {
                    revisionNumber = Integer.parseInt((String)queryMap.get("revision-number"));
                }

                if (queryMap.containsKey("delta")) {
                    delta = Integer.parseInt((String)queryMap.get("delta"));
                }

                if (queryMap.containsKey("meta")) {
                    metaString = (String)queryMap.get("meta");
                }

                isUpdateType = (delta != UNDEF_VALUE) && (delta < revisionNumber);


                // "/databases/id/items"                3 tokens
                // "/databases/id/containers"		3 tokens
                // "/databases/id/items/id.format"      4 tokens
                // "/databases/id/containers/id/items"  5 tokens
                if (path.equals("/databases")) {
                    requestType = DATABASES;

                } else if (path.startsWith("/databases")) {

                    PathTokenizer tok = new PathTokenizer(path);
                    int count = tok.countTokens();

                    if (count >= 3) {
                        String token = tok.nextToken();

                        if (token.equals("databases")==false && LOG.isWarnEnabled()) {
                            LOG.warn("Unknown token in path: " + path + " [" + token + "]@1");
                        }

                        databaseId = Integer.parseInt((String)tok.nextToken());
                        token = tok.nextToken();

                        if (token.equals("items")) {
                            requestType = DATABASE_SONGS;
                        } else if (token.equals("containers")) {
                            requestType = DATABASE_PLAYLISTS;
                        } else if (LOG.isWarnEnabled()) {
                            LOG.warn("Unknown token in path: " + path + " [" + token + "]@2");
                        }

                        if (count == 3) {
                            // do nothing...

                        } else if (count == 4) {

                            token = (String)tok.nextToken();

                            StringTokenizer fileTokenizer = new StringTokenizer(token, ".");

                            if (fileTokenizer.countTokens()==2) {
                                itemId = Integer.parseInt(fileTokenizer.nextToken());
                                requestType = SONG;

                            } else if(LOG.isWarnEnabled()) {
                                LOG.warn("Unknown token in path: " + path + " [" + token + "]@3");
                            }

                        } else if (count == 5) {
                            containerId = Integer.parseInt((String)tok.nextToken());
                            token = (String)tok.nextToken();

                            if (token.equals("items")) {
                                requestType = PLAYLIST_SONGS;

                            } else if (LOG.isWarnEnabled()) {
                                LOG.warn("Unknown token in path: " + path + " [" + token + "@4");
                            }

                        } else if (LOG.isWarnEnabled()) {
                            LOG.warn("Unknown token in path: " + path + " [" + token + "]@5");
                        }
                    } else  if (LOG.isWarnEnabled()) {
                        LOG.warn("Unknown token in path: " + path);
                    }
                }
            }

            this.queryMap = queryMap;
        
        } else {
            
            queryMap = null;
            metaString = null;
            isUpdateType = false;
            
            requestType = UNDEF_VALUE;
            databaseId = UNDEF_VALUE;
            containerId = UNDEF_VALUE;
            itemId = UNDEF_VALUE;
            
            sessionId = UNDEF_VALUE;
            revisionNumber = UNDEF_VALUE;
            delta = UNDEF_VALUE;
        }
    }
    
    /**
     *
     */
    public void addHeaders(Header[] headers) {
        for(int i = 0; i < headers.length; i++)
            this.headers.add(headers[i]);
    }
    
    /**
     *
     */
    public void addHeaders(List headers) {
        this.headers.addAll(headers);
    }
    
    /**
     *
     */
    public void addHeader(Header header) {
        if (header != null) {
            this.headers.add(header);
        }
    }
    
    /**
     *
     */
    public List getHeaders() {
        return headers;
    }
    
    /**
     *
     */
    public Header getHeader(String key) {
        
        Iterator it = headers.iterator();
        while(it.hasNext()) {
            Header header = (Header)it.next();
            if (header.getName().equals(key)) {
                return header;
            }
        }
        
        return null;
    }
    
    /**
     *
     */
    public boolean isUnknownRequest() {
        return (requestType==UNDEF_VALUE);
    }
    
    /**
     *
     */
    public boolean isServerInfoRequest() {
        return (requestType==SERVER_INFO);
    }
    
    /**
     *
     */
    public boolean isContentCodesRequest() {
        return (requestType==CONTENT_CODES);
    }
    
    /**
     *
     */
    public boolean isLoginRequest() {
        return (requestType==LOGIN);
    }
    
    /**
     *
     */
    public boolean isLogoutRequest() {
        return (requestType==LOGOUT);
    }
    
    /**
     *
     */
    public boolean isUpdateRequest() {
        return (requestType==UPDATE);
    }
    
    /**
     *
     */
    public boolean isResolveRequest() {
        return (requestType==RESOLVE);
    }
    
    /**
     *
     */
    public boolean isDatabasesRequest() {
        return (requestType==DATABASES);
    }
    
    /**
     *
     */
    public boolean isDatabaseSongsRequest() {
        return (requestType==DATABASE_SONGS);
    }
    
    /**
     *
     */
    public boolean isDatabasePlaylistsRequest() {
        return (requestType==DATABASE_PLAYLISTS);
    }
    
    /**
     *
     */
    public boolean isPlaylistSongsRequest() {
        return (requestType==PLAYLIST_SONGS);
    }
    
    /**
     *
     */
    public boolean isSongRequest() {
        return (requestType==SONG);
    }
    
    /**
     *
     */
    public URI getUri() {
        return uri;
    }
    
    /**
     *
     */
    public int getSessionId() {
        return sessionId;
    }
    
    /**
     *
     */
    public int getRevisionNumber() {
        return revisionNumber;
    }
    
    /**
     *
     */
    public int getDelta() {
        return delta;
    }
    
    /**
     *
     */
    public List getMeta() {
        // parse only if required...
        if (meta == null && metaString != null) {
            meta = DaapUtil.parseMeta(metaString);
            metaString = null;
        }
        
        return meta;
    }
    
    /**
     *
     */
    public int getDatabaseId() {
        return databaseId;
    }
    
    /**
     *
     */
    public int getContainerId() {
        return containerId;
    }
    
    /**
     *
     */
    public int getItemId() {
        return itemId;
    }
    
    /**
     *
     */
    public boolean isDatabaseIdSet() {
        return (databaseId != 0);
    }
    
    /**
     *
     */
    public boolean isContainerIdSet() {
        return (containerId != 0);
    }
    
    /**
     *
     */
    public boolean isItemIdSet() {
        return (itemId != 0);
    }
    
    /**
     *
     */
    public int getRequestType() {
        return requestType;
    }
    
    /**
     *
     */
    public Map getQueryMap() {
        return queryMap;
    }
    
    /**
     *
     */
    public boolean isServerSideRequest() {
        return isServerSideRequest;
    }
    
    /**
     *
     */
    public boolean isUpdateType() {
        return isUpdateType;
    }
    
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        
        buffer.append(uri).append("\n");
        
        if (headers != null) {
            for(int i = 0; i < headers.size(); i++)
                buffer.append(headers.get(i));
        }
        
        return buffer.toString();
    }
}
