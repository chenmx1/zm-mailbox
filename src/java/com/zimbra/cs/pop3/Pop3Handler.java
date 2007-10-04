/*
 * ***** BEGIN LICENSE BLOCK *****
 * 
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2004, 2005, 2006, 2007 Zimbra, Inc.
 * 
 * The contents of this file are subject to the Yahoo! Public License
 * Version 1.0 ("License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 * http://www.zimbra.com/license.
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * 
 * ***** END LICENSE BLOCK *****
 */

/*
 * Created on Nov 25, 2004
 */
package com.zimbra.cs.pop3;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ByteUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.AccessManager;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.Message;
import com.zimbra.cs.security.sasl.Authenticator;
import com.zimbra.cs.security.sasl.AuthenticatorUser;
import com.zimbra.cs.security.sasl.AuthenticatorUtil;
import com.zimbra.cs.security.sasl.GssAuthenticator;
import com.zimbra.cs.security.sasl.PlainAuthenticator;
import com.zimbra.cs.stats.ActivityTracker;
import com.zimbra.cs.stats.ZimbraPerf;
import com.zimbra.cs.tcpserver.ProtocolHandler;
import com.zimbra.cs.util.Config;
import org.apache.commons.codec.binary.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.InetAddress;

/**
 * @author schemers
 */
public abstract class Pop3Handler extends ProtocolHandler {
    static final int MIN_EXPIRE_DAYS = 31;
    static final int MAX_RESPONSE = 512;
    static final int MAX_RESPONSE_TEXT = MAX_RESPONSE - 7; // "-ERR" + " " + "\r\n"
    
    private static final byte[] LINE_SEPARATOR = { '\r', '\n'};
    private static final String TERMINATOR = ".";
    private static final int TERMINATOR_C = '.';    
    private static final byte[] TERMINATOR_BYTE = { '.' };

    private static final String MECHANISM_PLAIN = PlainAuthenticator.MECHANISM;
    private static final String MECHANISM_GSSAPI = GssAuthenticator.MECHANISM;
    
    // Connection specific data
    protected Pop3Config mConfig;
    protected OutputStream mOutputStream;
    protected boolean mStartedTLS;

    private String mUser;
    private String mQuery;
    private String mAccountId;
    private String mAccountName;
    private Pop3Mailbox mMbx;
    private String mCommand;
    private long mStartTime;
    protected int mState;
                      
    protected boolean dropConnection;
    protected Authenticator mAuthenticator;

    protected static final int STATE_AUTHORIZATION = 1;
    protected static final int STATE_TRANSACTION = 2;
    protected static final int STATE_UPDATE = 3;

    // Message specific data
    private String mCurrentCommandLine;
    private int mExpire;

    private static ActivityTracker sActivityTracker;

    private static synchronized void initActivityTracker() {
        if (sActivityTracker == null) {
            sActivityTracker = ActivityTracker.getInstance("pop3.csv");
        }
    }
    
    Pop3Handler(Pop3Server server) {
        super(server);
        mConfig = (Pop3Config) server.getConfig();
        initActivityTracker();
    }

    
    Pop3Handler(MinaPop3Server server) {
        super(null);
        mConfig = (Pop3Config) server.getConfig();
        initActivityTracker();
    }

   
    protected boolean authenticate() {
        // we auth with the USER/PASS commands
        return true;
    }

    protected boolean setupConnection(InetAddress remoteAddr)
            throws IOException {
        ZimbraLog.clearContext();
        ZimbraLog.addIpToContext(remoteAddr.getHostAddress());
        ZimbraLog.pop.info("connected");
        if (!Config.userServicesEnabled()) {
            dropConnection();
            return false;
        }
        sendOK(mConfig.getBanner());
        mState = STATE_AUTHORIZATION;
        dropConnection = false;
        return true;
    }
    
    protected boolean processCommand(String line) throws IOException {
        // TODO: catch IOException too?
        if (mAuthenticator != null && !mAuthenticator.isComplete()) {
            return continueAuthentication(line);
        }
        
        mCommand = null;
        mStartTime = 0;
        mCurrentCommandLine = line;
        
        try {
            if (mAccountName != null) ZimbraLog.addAccountNameToContext(mAccountName);
            boolean result = processCommandInternal();

            // Track stats if the command completed successfully
            if (mStartTime > 0) {
                ZimbraPerf.STOPWATCH_POP.stop(mStartTime);
                if (mCommand != null) {
                    sActivityTracker.addStat(mCommand.toUpperCase(), mStartTime);
                }
            }

            return result;
        } catch (Pop3CmdException e) {
            sendERR(e.getResponse());
            ZimbraLog.pop.debug(e.getMessage(), e);
            return !dropConnection;
        } catch (ServiceException e) {
            sendERR(Pop3CmdException.getResponse(e.getMessage()));
            ZimbraLog.pop.debug(e.getMessage(), e);
            return !dropConnection;
        } finally {
            ZimbraLog.clearContext();
        }
    }
    
    protected boolean processCommandInternal() throws Pop3CmdException, IOException, ServiceException {
        mStartTime = System.currentTimeMillis();
        //ZimbraLog.pop.info("command("+mCurrentCommandLine+")");
        mCommand = mCurrentCommandLine;
        String arg = null;

        if (mCommand == null) {
            dropConnection = true;
            ZimbraLog.pop.info("disconnected without quit");
            //dropConnection();
            return false;
        }

        if (!Config.userServicesEnabled()) {
            dropConnection = true;
            sendERR("Temporarily unavailable");
            //dropConnection();
            return false;
        }

        setIdle(false);

        int space = mCommand.indexOf(" ");
        if (space > 0) {
            arg = mCommand.substring(space + 1); 
            mCommand = mCommand.substring(0, space);
        }

        if (ZimbraLog.pop.isDebugEnabled()) {
            String darg = "PASS".equals(mCommand) ? "<BLOCKED>" : arg;
            ZimbraLog.pop.debug("command=%s arg=%s", mCommand, darg);
        }

        if (mCommand.length() < 1)
            throw new Pop3CmdException("invalid request. please specify a command");

        // check account status before executing command
        if (mAccountId != null)
            try {
                Account acct = Provisioning.getInstance().get(AccountBy.id, mAccountId);
                if (acct == null || !acct.getAccountStatus().equals(Provisioning.ACCOUNT_STATUS_ACTIVE))
                    return false;
            } catch (ServiceException e) {
                return false;
            }

        int ch = mCommand.charAt(0);
        
        // Breaking out of this switch causes a syntax error to be returned
        // So if you process a command then return immediately (even if the
        // command handler reported a syntax error or failed otherwise)
        
        switch (ch) {
        case 'a':
        case 'A':
            if ("AUTH".equalsIgnoreCase(mCommand)) {
                doAUTH(arg);
                return true;
            }
        case 'c':
        case 'C':
            if ("CAPA".equalsIgnoreCase(mCommand)) {
                doCAPA();
                return true;
            }
            break;
        case 'd':
        case 'D':
            if ("DELE".equalsIgnoreCase(mCommand)) {
                doDELE(arg);
                return true;
            }
            break;            
        case 'l':
        case 'L':
            if ("LIST".equalsIgnoreCase(mCommand)) {
                doLIST(arg);
                return true;
            }
            break;                                    
        case 'n':
        case 'N':
            if ("NOOP".equalsIgnoreCase(mCommand)) {
                doNOOP();
                return true;
            }
            break;
        case 'p':
        case 'P':
            if ("PASS".equalsIgnoreCase(mCommand)) {
                doPASS(arg);
                return true;
            }
            break;
        case 'q':
        case 'Q':
            if ("QUIT".equalsIgnoreCase(mCommand)) {
                doQUIT();
                return false;
            }
            break;
        case 'r':
        case 'R':
            if ("RETR".equalsIgnoreCase(mCommand)) {
                doRETR(arg);
                return true;
            } else if ("RSET".equalsIgnoreCase(mCommand)) {
                doRSET();
                return true;
            }
            break;            
        case 's':
        case 'S':
            if ("STAT".equalsIgnoreCase(mCommand)) {
                doSTAT();
                return true;
            } else if ("STLS".equalsIgnoreCase(mCommand)) {
                doSTLS();
                return true;
            }
            break;            
        case 't':
        case 'T':
            if ("TOP".equalsIgnoreCase(mCommand)) {
                doTOP(arg);
                return true;
            }
            break;                        
        case 'u':
        case 'U':
            if ("UIDL".equalsIgnoreCase(mCommand)) {
                doUIDL(arg);
                return true;
            } else if ("USER".equalsIgnoreCase(mCommand)) {
                doUSER(arg);
                return true;            
            }
            break;
            
        default:
            break;
        }
        throw new Pop3CmdException("unknown command");        
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.tcpserver.ProtocolHandler#notifyIdleConnection()
     */
    protected void notifyIdleConnection() {
        // according to RFC 1939 we aren't supposed to snd a response on idle timeout
        ZimbraLog.pop.debug("idle connection");

    }

    protected void sendERR(String response) throws IOException {
        sendResponse("-ERR", response, true);
    }

    protected void sendOK(String response) throws IOException {
        sendResponse("+OK", response, true);        
    }
    
    private void sendOK(String response, boolean flush) throws IOException {
        sendResponse("+OK", response, flush);        
    }

    protected void sendContinuation(String s) throws IOException {
        sendLine("+ " + s, true);
    }
    
    private void sendResponse(String status, String msg, boolean flush) throws IOException {
        String cl = mCurrentCommandLine != null ? mCurrentCommandLine : "<none>";
        String response = (msg == null || msg.length() == 0) ? status : status+" "+msg;
        if (ZimbraLog.pop.isDebugEnabled()) {
            ZimbraLog.pop.debug("%s (%s)", response, cl);
        } else {
            // only log errors if not debugging...
            if (status.charAt(0) == '-') {
                if (cl.toUpperCase().startsWith("PASS")) {
                    cl = "PASS ****";
                }
                ZimbraLog.pop.info("%s (%s)", response, cl);
            }
        }        
        sendLine(response, flush);
    }

    private void sendLine(String line) throws IOException {
        sendLine(line, true);
    }
    
    private void sendLine(String line, boolean flush) throws IOException {
        mOutputStream.write(line.getBytes());
        mOutputStream.write(LINE_SEPARATOR);
        if (flush)
            mOutputStream.flush();
    }

    /*
     * state:
     *   in a line
     *   hit end of a line
     */
    private void sendMessage(InputStream is, int maxNumBodyLines) throws IOException {
        
        boolean inBody = false;
        int numBodyLines = 0;
        
        PushbackInputStream stream = new PushbackInputStream(is);
        int c;
        
        boolean startOfLine = true;
        int lineLength = 0;

        while ((c = stream.read()) != -1) {
            if (c == '\r' || c == '\n') {
                if (c == '\r') {
                    int peek = stream.read();
                    if (peek != '\n' && peek != -1) stream.unread(peek);
                }
    
                if (!inBody) {
                    if (lineLength == 0)
                        inBody = true;
                } else {
                    numBodyLines++;
                }                         
                if (inBody && numBodyLines >= maxNumBodyLines)
                    break;
                startOfLine = true;
                lineLength = 0;
                mOutputStream.write(LINE_SEPARATOR);
                continue;
            } else if (c == TERMINATOR_C && startOfLine) {
                mOutputStream.write(c); // we'll end up writing it twice
            }
            if (startOfLine) startOfLine = false;
            lineLength++;
            mOutputStream.write(c);
        }     
        if (lineLength != 0) mOutputStream.write(LINE_SEPARATOR);
        mOutputStream.write(TERMINATOR_BYTE);
        mOutputStream.write(LINE_SEPARATOR);
        mOutputStream.flush();
    }

    private void doQUIT() throws IOException, ServiceException, Pop3CmdException {
        dropConnection = true;
        if (mMbx != null && mMbx.getNumDeletedMessages() > 0) {
            mState = STATE_UPDATE;
            // TODO: hard/soft could be a user/cos pref
            int count = mMbx.deleteMarked(true);
            sendOK("deleted "+count+" message(s)");
        } else {
            sendOK(mConfig.getGoodbye());
        }
        ZimbraLog.pop.info("quit from client");
        //dropConnection();
    }
    
    private void doNOOP() throws IOException {
        sendOK("yawn");
    }
    
    private void doRSET() throws Pop3CmdException, IOException {
        if (mState != STATE_TRANSACTION) 
            throw new Pop3CmdException("this command is only valid after a login");
        int numUndeleted = mMbx.undeleteMarked();
        sendOK(numUndeleted+ " message(s) undeleted");
    }    
    
    private void doUSER(String user) throws Pop3CmdException, IOException, ServiceException {
        checkIfLoginPermitted();
        
        if (mState != STATE_AUTHORIZATION)
            throw new Pop3CmdException("this command is only valid in authorization state");
        
        if (user == null)
            throw new Pop3CmdException("please specify a user");

        if (user.length() > 1024)
            throw new Pop3CmdException("username length too long");

        if (user.endsWith("}")) {
            int p = user.indexOf('{');
            if (p != -1) {
                mUser = user.substring(0, p);
                mQuery = user.substring(p+1, user.length()-1);
                //mLog.info("mUser("+mUser+") mQuery("+mQuery+")");
            } else {
                mUser = user;
            }
        } else {
            mUser = user;
        }

        sendOK("hello "+mUser+", please enter your password");
    }
    
    private void doPASS(String password) throws Pop3CmdException, IOException {
        checkIfLoginPermitted();
        
        if (mState != STATE_AUTHORIZATION)
            throw new Pop3CmdException("this command is only valid in authorization state");
        
        if (mUser == null)
            throw new Pop3CmdException("please specify username first with the USER command");

        if (password == null)
            throw new Pop3CmdException("please specify a password");

        if (password.length() > 1024)
            throw new Pop3CmdException("password length too long");

        authenticate(mUser, "", password, null);
        sendOK("server ready");
    }

    private void doAUTH(String arg) throws IOException {
        if (isAuthenticated()) {
            sendERR("command only valid in AUTHORIZATION state");
            return;
        }
        int i = arg.indexOf(' ');
        String mechanism = i > 0 ? arg.substring(0, i) : arg;
        String initialResponse = i > 0 ? arg.substring(i + 1) : null;
        AuthenticatorUser authUser = new Pop3AuthenticatorUser(this);
        if (MECHANISM_PLAIN.equals(mechanism)) {
            if (!allowCleartextLogins()) {
                sendERR("cleartext logins disabled");
                return;
            }
            mAuthenticator = new PlainAuthenticator(authUser);
        } else if (MECHANISM_GSSAPI.equals(mechanism) && isGssAuthEnabled()) {
            mAuthenticator = new GssAuthenticator(authUser);
        } else {
            sendERR("mechanism not supported");
            return;
        }
        if (!mAuthenticator.initialize()) {
            mAuthenticator = null;
            return;
        }
        if (initialResponse != null) {
            continueAuthentication(initialResponse);
        } else {
            sendContinuation("");
        }
    }

    private boolean allowCleartextLogins() {
        return mStartedTLS || mConfig.allowCleartextLogins() || mConfig.isSSLEnabled();
    }
    
    private boolean continueAuthentication(String response) throws IOException {
        byte[] b = Base64.decodeBase64(response.getBytes("us-ascii"));
        mAuthenticator.handle(b);
        if (mAuthenticator.isComplete()) {
            if (mAuthenticator.isAuthenticated()) {
                completeAuthentication();
            } else {
                mAuthenticator = null;
            }
        }
        return true;
    }

    private boolean isAuthenticated() {
        return mState != STATE_AUTHORIZATION && mAccountId != null;
    }

    // TODO Remove this debugging option for final release
    private static final Boolean GSS_ENABLED = Boolean.getBoolean("ZimbraGssEnabled");

    private boolean isGssAuthEnabled() {
        return GSS_ENABLED || mConfig.isSaslGssapiEnabled();
    }
    
    protected void authenticate(String username,
                                String authenticateId,
                                String password,
                                String mechanism)
            throws Pop3CmdException, IOException {
        String type = mechanism != null ? "authentication" : "login";
        try {
            Account acct = MECHANISM_GSSAPI.equals(mechanism) ?
                AuthenticatorUtil.authenticateKrb5(username, authenticateId) :
                AuthenticatorUtil.authenticate(username, authenticateId, password, "pop3");
            if (acct == null)
                throw new Pop3CmdException(type + " failed");
            if (!acct.getBooleanAttr(Provisioning.A_zimbraPop3Enabled, false))
                throw new Pop3CmdException("pop access not enabled for account");
            mAccountId = acct.getId();
            mAccountName = acct.getName();
            Mailbox mailbox = MailboxManager.getInstance().getMailboxByAccountId(mAccountId);
            mMbx = new Pop3Mailbox(mailbox, acct, mQuery);
            mState = STATE_TRANSACTION;
            mExpire = (int) (acct.getTimeInterval(Provisioning.A_zimbraMailMessageLifetime, 0) / (1000*60*60*24));
            if (mExpire > 0 && mExpire < MIN_EXPIRE_DAYS) mExpire = MIN_EXPIRE_DAYS;
        } catch (ServiceException e) {
            // need to catch and mask these two
            String code = e.getCode();
            if (code.equals(AccountServiceException.NO_SUCH_ACCOUNT) ||
                code.equals(AccountServiceException.AUTH_FAILED)) {
                throw new Pop3CmdException("invalid username/password");
            } else if (code.equals(AccountServiceException.CHANGE_PASSWORD)) {
                throw new Pop3CmdException("your password has expired");
            } else if (code.equals(AccountServiceException.MAINTENANCE_MODE)) {
                throw new Pop3CmdException("your account is having maintenance peformed. please try again later");
            } else {
                throw new Pop3CmdException(e.getMessage());
            }
        }
    }

    private void checkIfLoginPermitted() throws Pop3CmdException {
        if (!allowCleartextLogins())
            throw new Pop3CmdException("only valid after entering TLS mode");        
    }

    protected boolean isSSLEnabled() {
        return mConfig.isSSLEnabled();
    }
        
    private void doSTAT() throws Pop3CmdException, IOException {
        if (mState != STATE_TRANSACTION) 
            throw new Pop3CmdException("this command is only valid after a login");
        sendOK(mMbx.getNumMessages()+" "+mMbx.getSize());
    }
    
    private void doSTLS() throws Pop3CmdException, IOException {
        if (mConfig.isSSLEnabled())
            throw new Pop3CmdException("command not valid over SSL");

        if (mState != STATE_AUTHORIZATION)
            throw new Pop3CmdException("this command is only valid prior to login");
        if (mStartedTLS)
            throw new Pop3CmdException("command not valid while in TLS mode");
        startTLS();
        mStartedTLS = true;
    }

    private void doLIST(String msg) throws Pop3CmdException, IOException {
        if (mState != STATE_TRANSACTION) 
            throw new Pop3CmdException("this command is only valid after a login");
        if (msg != null) {
            Pop3Message pm = mMbx.getPop3Msg(msg);
            sendOK(msg+" "+pm.getSize());
        } else {
            sendOK(mMbx.getNumMessages()+" messages", false);
            int totNumMsgs = mMbx.getTotalNumMessages();
            for (int n=0; n < totNumMsgs; n++) {
                Pop3Message pm = mMbx.getMsg(n);                
                if (!pm.isDeleted())
                    sendLine((n+1)+" "+pm.getSize(), false);
            }
            sendLine(TERMINATOR);
        }
    }
    
    private void doUIDL(String msg) throws Pop3CmdException, IOException {
        if (mState != STATE_TRANSACTION) 
            throw new Pop3CmdException("this command is only valid after a login");
        if (msg != null) {
            Pop3Message pm = mMbx.getPop3Msg(msg);
            sendOK(msg+" "+pm.getId()+"."+pm.getDigest());
        } else {
            sendOK(mMbx.getNumMessages()+" messages", false);
            int totNumMsgs = mMbx.getTotalNumMessages();
            for (int n=0; n < totNumMsgs; n++) {
                Pop3Message pm = mMbx.getMsg(n);                
                if (!pm.isDeleted())
                    sendLine((n+1)+" "+pm.getId()+"."+pm.getDigest(), false);
            }
            sendLine(TERMINATOR);
        }
    }            

    private int parseInt(String s, String message) throws Pop3CmdException {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new Pop3CmdException(message);
        }
    }

    private void doRETR(String msg) throws Pop3CmdException, IOException, ServiceException {
        if (mState != STATE_TRANSACTION) 
            throw new Pop3CmdException("this command is only valid after a login");

        if (msg == null)
            throw new Pop3CmdException("please specify a message");

        Message m = mMbx.getMessage(msg);
        InputStream is = null;        
        try {
            is = m.getContentStream();
            sendOK("message follows", false);
            sendMessage(is, Integer.MAX_VALUE);
        } finally {
            ByteUtil.closeStream(is);
        }
    }

    private void doTOP(String arg) throws Pop3CmdException, IOException, ServiceException {
        if (mState != STATE_TRANSACTION) 
            throw new Pop3CmdException("this command is only valid after a login");
        
        int space = arg == null ? -1 : arg.indexOf(" ");
        if (space == -1)
            throw new Pop3CmdException("please specify a message and number of lines");
        
        String msg = arg.substring(0, space);
        int n = parseInt(arg.substring(space + 1), "unable to parse number of lines");
        
        if (n < 0) 
            throw new Pop3CmdException("please specify a non-negative value for number of lines");

        if (msg == null || msg.equals(""))
            throw new Pop3CmdException("please specify a message");

        Message m = mMbx.getMessage(msg);
        InputStream is = null;        
        try {
            is = m.getContentStream();
            sendOK("message top follows", false);
            sendMessage(is, n);
        } finally {
            ByteUtil.closeStream(is);
        }
    }

    private void doDELE(String msg) throws Pop3CmdException, IOException {
        if (mState != STATE_TRANSACTION) 
            throw new Pop3CmdException("this command is only valid after a login");

        if (msg == null)
            throw new Pop3CmdException("please specify a message");

        Pop3Message pm = mMbx.getPop3Msg(msg);
        mMbx.delete(pm);
        sendOK("message "+msg+" marked for deletion");
    }    

    private void doCAPA() throws IOException {
        // [SASL]       RFC 5034: POP3 Simple Authentication and Security Layer (SASL) Authentication Mechanism
        sendOK("Capability list follows", false);
        sendLine("TOP", false);
        sendLine("USER", false);
        sendLine("UIDL", false);
        if (!mConfig.isSSLEnabled()) {
            sendLine("STLS", false);
        }
        String sasl = getSaslCapabilities();
        if (sasl != null) {
            sendLine("SASL" + sasl, false);
        }
        if (mState != STATE_TRANSACTION) {
            sendLine("EXPIRE "+MIN_EXPIRE_DAYS+" USER", false);
        } else {
            if (mExpire == 0)
                sendLine("EXPIRE NEVER", false);                
            else
                sendLine("EXPIRE "+mExpire, false);
        }
        // TODO: VERSION INFO
        sendLine("IMPLEMENTATION ZimbraInc", false);
        sendLine(TERMINATOR);
    }

    private String getSaslCapabilities() {
        StringBuilder sb = new StringBuilder();
        if (allowCleartextLogins()) sb.append(" PLAIN");
        if (isGssAuthEnabled()) sb.append(" GSSAPI");
        return sb.toString();
    }
    
    protected abstract void startTLS() throws IOException;

    protected abstract void completeAuthentication() throws IOException;
}
