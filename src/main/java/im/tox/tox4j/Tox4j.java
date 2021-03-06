package im.tox.tox4j;

import com.google.protobuf.InvalidProtocolBufferException;
import im.tox.tox4j.callbacks.*;
import im.tox.tox4j.exceptions.*;
import im.tox.tox4j.proto.Events;

/**
 * Implementation of a simple 1:1 Wrapper for the Tox API
 * <p>
 * Due to the underlying native implementation, one should avoid excessively creating new instances and quickly calling
 * {@link #close()} on them again. If excessive creation and closing of new tox instance is deemed necessary, the calls
 * to {@link #close()} should be done in reverse order of creation in order to preserve memory. The memory overhead is
 * insignificant, unless you create hundreds of instances and don't dispose of them correctly in the course of a running
 * application.
 *
 * @author Simon Levermann (sonOfRa)
 */
public class Tox4j implements ToxSimpleChat {

    static {
        System.loadLibrary("tox4j");
    }

    /**
     * Internal instance number
     */
    private final int instanceNumber;

    private FriendRequestCallback friendRequestCallback;
    private MessageCallback messageCallback;
    private ActionCallback actionCallback;
    private NameChangeCallback nameChangeCallback;
    private StatusMessageCallback statusMessageCallback;
    private UserStatusCallback userStatusCallback;
    private TypingChangeCallback typingChangeCallback;
    private ConnectionStatusCallback connectionStatusCallback;
    private GroupInviteCallback groupInviteCallback;
    private GroupMessageCallback groupMessageCallback;
    private GroupActionCallback groupActionCallback;
    private GroupTitleChangeCallback groupTitleChangeCallback;

    /**
     * Calls kill() on every tox instance. This will invalidate all instances without notice, and should only be
     * used during testing or debugging.
     */
    static native void destroyAll();

    private static native void finalize(int instanceNumber);

    @Override
    public final void finalize() throws Throwable {
        try {
            finalize(instanceNumber);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        super.finalize();
    }

    private static native int toxNew(boolean ipv6Enabled, boolean udpDisabled, boolean proxyEnabled, String proxyAddress, int proxyPort);

    /**
     * Creates a new Tox4j instance with the default settings:
     * <ul>
     * <li>IPv6 is enabled</li>
     * <li>UDP is not disabled</li>
     * <li>Proxy is not enabled</li>
     * <li>Proxy Address is empty</li>
     * <li>Proxy port is 0</li>
     * </ul>
     *
     * @throws im.tox.tox4j.exceptions.ToxException if the instance could not be created.
     */
    public Tox4j() throws ToxException {
        this(true, false, false, null, 0);
    }

    /**
     * Creates a new Tox4j instance with no proxy
     *
     * @param ipv6Enabled true to enable IPv6
     * @param udpDisabled true to disable UDP
     * @throws im.tox.tox4j.exceptions.ToxException if the instance could not be created.
     */
    public Tox4j(boolean ipv6Enabled, boolean udpDisabled) throws ToxException {
        this(ipv6Enabled, udpDisabled, false, null, 0);
    }

    /**
     * Creates a new tox instance with the specified settings
     *
     * @param ipv6Enabled  true to enable IPv6
     * @param udpDisabled  true to disable UDP (needed when running over Tor)
     * @param proxyEnabled true to enable Proxy (SOCKS5 is the only supported proxy as of now)
     * @param proxyAddress address of the Proxy (IPv4, IPv6, hostname). Must not be null if proxyEnabled is true
     * @param proxyPort    port for the Proxy
     * @throws im.tox.tox4j.exceptions.ToxException if the instance could not be created, for example due to an invalid port or proxy address
     * @throws java.lang.IllegalArgumentException   if proxy is enabled, and the address is null or empty, or the proxy port is not a valid port
     */
    public Tox4j(boolean ipv6Enabled, boolean udpDisabled, boolean proxyEnabled, String proxyAddress, int proxyPort) throws ToxException {
        if (proxyEnabled) {
            if (proxyAddress == null || proxyAddress.trim().isEmpty()) {
                throw new IllegalArgumentException("Proxy address cannot be empty if proxy is enabled");
            }

            validatePort(proxyPort);
        }

        int result = toxNew(ipv6Enabled, udpDisabled, proxyEnabled, proxyAddress, proxyPort);

        if (result == -1) {
            throw new ToxException("Creating the new tox instance failed");
        } else {
            this.instanceNumber = result;
        }
    }

    /**
     * Validate a port
     *
     * @param port the port number
     * @throws IllegalArgumentException unless  1 &lt;= port &lt;= 65535
     */
    private static void validatePort(int port) throws IllegalArgumentException {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port invalid");
        }
    }

    /**
     * Validate an address
     *
     * @param address the address
     * @throws IllegalArgumentException if the address is null or blank
     */
    private static void validateAddress(String address) throws IllegalArgumentException {
        if (address == null) {
            throw new IllegalArgumentException("Address cannot be null");
        }

        if (address.trim().isEmpty()) {
            throw new IllegalArgumentException("Address cannot be empty");
        }
    }

    /**
     * Validate a public key
     *
     * @param publicKey the public key
     * @throws IllegalArgumentException if the key is null or the wrong size
     */
    private static void validatePublicKey(byte[] publicKey) throws IllegalArgumentException {
        if (publicKey == null) {
            throw new IllegalArgumentException("Public key cannot be null");
        }

        if (publicKey.length != ToxConstants.CLIENT_ID_SIZE) {
            throw new IllegalArgumentException("Public key size is invalid");
        }
    }

    private static native int bootstrap(int instanceNumber, String address, int port, byte[] publicKey);

    @Override
    public void bootstrap(String address, int port, byte[] publicKey) throws ToxException, IllegalArgumentException {
        validateAddress(address);
        validatePort(port);
        validatePublicKey(publicKey);

        int result = bootstrap(this.instanceNumber, address, port, publicKey);

        if (result == 0) {
            throw new ToxException("Could not resolve address");
        }
    }

    private static native int addTcpRelay(int instanceNumber, String address, int port, byte[] publicKey);

    @Override
    public void addTcpRelay(String address, int port, byte[] publicKey) throws ToxException, IllegalArgumentException {
        validateAddress(address);
        validatePort(port);
        validatePublicKey(publicKey);

        int result = addTcpRelay(this.instanceNumber, address, port, publicKey);

        if (result == 0) {
            throw new ToxException("Could not resolve address");
        }
    }

    private static native boolean isConnected(int instanceNumber);

    @Override
    public boolean isConnected() {
        return isConnected(this.instanceNumber);
    }

    private static native void kill(int instanceNumber);

    @Override
    public void close() {
        kill(this.instanceNumber);
    }

    private static native int doInterval(int instanceNumber);

    @Override
    public int doInterval() {
        return doInterval(this.instanceNumber);
    }

    private static native byte[] toxDo(int instanceNumber);

    @Override
    public void toxDo() {
        byte[] events = toxDo(this.instanceNumber);
        Events.ToxEvents toxEvents;
        try {
            toxEvents = Events.ToxEvents.parseFrom(events);
        } catch (InvalidProtocolBufferException e) {
            toxEvents = Events.ToxEvents.getDefaultInstance();
        }

        if (this.friendRequestCallback != null) {
            for (Events.FriendRequest friendRequest : toxEvents.getFriendRequestList()) {
                this.friendRequestCallback.execute(friendRequest.getAddress().toByteArray(), friendRequest.getData().toByteArray());
            }
        }
        if (this.messageCallback != null) {
            for (Events.Message message : toxEvents.getMsgList()) {
                this.messageCallback.execute(message.getFriendNumber(), message.getData().toByteArray());
            }
        }
        if (this.actionCallback != null) {
            for (Events.Action action : toxEvents.getActionList()) {
                this.actionCallback.execute(action.getFriendNumber(), action.getAction().toByteArray());
            }
        }
        if (this.nameChangeCallback != null) {
            for (Events.NameChange nameChange : toxEvents.getNameChangeList()) {
                this.nameChangeCallback.execute(nameChange.getFriendNumber(), nameChange.getNewName().toByteArray());
            }
        }
        if (this.statusMessageCallback != null) {
            for (Events.StatusMessage statusMessage : toxEvents.getSMsgList()) {
                this.statusMessageCallback.execute(statusMessage.getFriendNumber(), statusMessage.getStatus().toByteArray());
            }
        }
        if (this.userStatusCallback != null) {
            for (Events.UserStatus userStatus : toxEvents.getUStatusList()) {
                this.userStatusCallback.execute(userStatus.getFriendNumber(), userStatus.getStatus());
            }
        }
        if (this.typingChangeCallback != null) {
            for (Events.TypingStatus typingStatus : toxEvents.getTStatusList()) {
                this.typingChangeCallback.execute(typingStatus.getFriendNumber(), typingStatus.getTyping());
            }
        }
        if (this.connectionStatusCallback != null) {
            for (Events.ConnectionStatus connectionStatus : toxEvents.getCStatusList()) {
                this.connectionStatusCallback.execute(connectionStatus.getFriendNumber(), connectionStatus.getStatus());
            }
        }
    }

    private static native byte[] save(int instanceNumber);

    @Override
    public byte[] save() {
        return save(this.instanceNumber);
    }

    private static native boolean load(int instanceNumber, byte[] data);

    @Override
    public void load(byte[] data) throws EncryptedSaveDataException {
        if (!load(this.instanceNumber, data)) {
            throw new EncryptedSaveDataException("Save data was encrypted!");
        }
    }

    @Override
    public void load(byte[] data, byte[] password) {
        throw new UnsupportedOperationException("Loading encrypted Save files is currently unsupported");
    }

    private static native byte[] getAddress(int instanceNumber);

    @Override
    public byte[] getAddress() {
        return getAddress(instanceNumber);
    }

    private static void validateToxAddress(byte[] address) throws IllegalArgumentException {
        if (address == null) {
            throw new IllegalArgumentException("Tox address must not be null");
        }

        if (address.length != ToxConstants.TOX_ADDRESS_SIZE) {
            throw new IllegalArgumentException("Invalid size for tox address");
        }
    }

    private static void handleAddFriendError(int errorCode) throws FriendAddException {
        switch (errorCode) {
            case -1:
                throw new FriendAddException(FriendAddErrorCode.TOOLONG, "Message too long");
            case -2:
                throw new FriendAddException(FriendAddErrorCode.NOMESSAGE, "Missing message");
            case -3:
                throw new FriendAddException(FriendAddErrorCode.OWNKEY, "Cannot add our own key");
            case -4:
                throw new FriendAddException(FriendAddErrorCode.ALREADYSENT, "Friend request already sent");
            case -6:
                throw new FriendAddException(FriendAddErrorCode.BADCHECKSUM, "Invalid checksum in address");
            case -7:
                throw new FriendAddException(FriendAddErrorCode.SETNEWNOSPAM, "Invalid nospam in address");
            case -8:
                // Throw OutOfMemoryError here?
                throw new FriendAddException(FriendAddErrorCode.NOMEM, "Could not allocate memory for new friend");
            default:
                throw new FriendAddException(FriendAddErrorCode.UNSPECIFIED, "Unknown error occurred");
        }
    }

    private static native int addFriend(int instanceNumber, byte[] address, byte[] message);

    @Override
    public int addFriend(byte[] address, byte[] message) throws FriendAddException, IllegalArgumentException {
        validateToxAddress(address);
        if (message == null) {
            throw new IllegalArgumentException("Friend request message cannot be null");
        }
        if (message.length == 0) {
            throw new IllegalArgumentException("Friend request message cannot be empty");
        }
        if (message.length > ToxConstants.MAX_FRIENDREQUEST_LENGTH) {
            throw new IllegalArgumentException("Friend request message size exceeded");
        }

        int errorCode = addFriend(this.instanceNumber, address, message);
        if (errorCode < 0) {
            handleAddFriendError(errorCode);
        }

        return errorCode;
    }

    private static native int addFriendNoRequest(int instanceNumber, byte[] clientId);

    @Override
    public int addFriendNoRequest(byte[] clientId) throws FriendAddException, IllegalArgumentException {
        validatePublicKey(clientId);
        int errorCode = addFriendNoRequest(this.instanceNumber, clientId);
        if (errorCode < 0) {
            handleAddFriendError(errorCode);
        }

        return errorCode;
    }

    private static native int getFriendNumber(int instanceNumber, byte[] clientId);

    @Override
    public int getFriendNumber(byte[] clientId) throws ToxException, IllegalArgumentException {
        validatePublicKey(clientId);
        int errorCode = getFriendNumber(this.instanceNumber, clientId);
        if (errorCode == -1) {
            throw new ToxException("No friend with specified client ID found");
        }
        return errorCode;
    }

    private static native byte[] getClientId(int instanceNumber, int friendNumber);

    @Override
    public byte[] getClientId(int friendNumber) throws NoSuchFriendException {
        byte[] clientId = getClientId(this.instanceNumber, friendNumber);
        if (clientId == null) {
            throw new NoSuchFriendException("Friend with specified friend number does not exist:" + friendNumber);
        }
        return clientId;
    }

    @Override
    public void deleteFriend(int friendNumber) throws NoSuchFriendException {

    }

    @Override
    public boolean getConnectionStatus(int friendNumber) throws NoSuchFriendException {
        return false;
    }

    @Override
    public boolean friendExists(int friendNumber) {
        return false;
    }

    @Override
    public int sendMessage(int friendNumber, byte[] message) throws ToxException, IllegalArgumentException, NoSuchFriendException {
        return 0;
    }

    @Override
    public int sendAction(int friendNumber, byte[] action) throws ToxException, IllegalArgumentException {
        return 0;
    }

    @Override
    public void setName(byte[] name) throws IllegalArgumentException {

    }

    @Override
    public byte[] getName() throws ToxException {
        return new byte[0];
    }

    @Override
    public byte[] getName(int friendNumber) throws ToxException, NoSuchFriendException {
        return new byte[0];
    }

    @Override
    public void setStatusMessage(byte[] statusMessage) throws IllegalArgumentException {

    }

    @Override
    public byte[] getStatusMessage() throws ToxException {
        return new byte[0];
    }

    @Override
    public byte[] getStatusMessage(int friendNumber) throws ToxException, NoSuchFriendException {
        return new byte[0];
    }

    @Override
    public void setUserStatus(int userStatus) throws IllegalArgumentException {

    }

    @Override
    public int getUserStatus() {
        return 0;
    }

    @Override
    public int getUserStatus(int friendNumber) throws ToxException, NoSuchFriendException {
        return 0;
    }

    @Override
    public long lastSeen(int friendNumber) throws ToxException, NoSuchFriendException {
        return 0;
    }

    @Override
    public void setTypingStatus(int friendNumber, boolean typing) throws ToxException {

    }

    @Override
    public boolean getTypingStatus(int friendNumber) throws ToxException, NoSuchFriendException {
        return false;
    }

    @Override
    public int[] getFriendList() {
        return new int[0];
    }

    @Override
    public void registerFriendRequestCallback(FriendRequestCallback callback) {
        this.friendRequestCallback = callback;
    }

    @Override
    public void registerMessageCallback(MessageCallback callback) {
        this.messageCallback = callback;
    }

    @Override
    public void registerActionCallback(ActionCallback callback) {
        this.actionCallback = callback;
    }

    @Override
    public void registerNameChangeCallback(NameChangeCallback callback) {
        this.nameChangeCallback = callback;
    }

    @Override
    public void registerStatusMessageCallback(StatusMessageCallback callback) {
        this.statusMessageCallback = callback;
    }

    @Override
    public void registerUserStatusCallback(UserStatusCallback callback) {
        this.userStatusCallback = callback;
    }

    @Override
    public void registerTypingChangeCallback(TypingChangeCallback callback) {
        this.typingChangeCallback = callback;
    }

    @Override
    public void registerConnectionStatusCallback(ConnectionStatusCallback callback) {
        this.connectionStatusCallback = callback;
    }

    @Override
    public void registerGroupInviteCallback(GroupInviteCallback callback) {
        this.groupInviteCallback = callback;
    }

    @Override
    public void registerGroupMessageCallback(GroupMessageCallback callback) {
        this.groupMessageCallback = callback;
    }

    @Override
    public void registerGroupActionCallback(GroupActionCallback callback) {
        this.groupActionCallback = callback;
    }

    @Override
    public void registerGroupTitleChangeCallback(GroupTitleChangeCallback callback) {
        this.groupTitleChangeCallback = callback;
    }
}
