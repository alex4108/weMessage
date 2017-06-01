package scott.wemessage.app.connection;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;

import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import scott.wemessage.R;
import scott.wemessage.app.AppLogger;
import scott.wemessage.app.security.CryptoType;
import scott.wemessage.app.security.DecryptionTask;
import scott.wemessage.app.security.EncryptionTask;
import scott.wemessage.app.security.KeyTextPair;
import scott.wemessage.app.weMessage;
import scott.wemessage.commons.json.connection.ClientMessage;
import scott.wemessage.commons.json.connection.InitConnect;
import scott.wemessage.commons.json.connection.ServerMessage;
import scott.wemessage.commons.json.message.security.JSONEncryptedText;
import scott.wemessage.commons.types.DeviceType;
import scott.wemessage.commons.types.DisconnectReason;
import scott.wemessage.commons.utils.ByteArrayAdapter;

public class ConnectionThread extends Thread {

    private final String TAG = ConnectionService.TAG;
    private final Object serviceLock = new Object();
    private final Object socketLock = new Object();
    private final Object inputStreamLock = new Object();
    private final Object outputStreamLock = new Object();

    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private AtomicBoolean hasTriedAuthenticating = new AtomicBoolean(false);
    private AtomicBoolean isConnected = new AtomicBoolean(false);
    private ConcurrentHashMap<String, ClientMessage>clientMessagesMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, ServerMessage>serverMessagesMap = new ConcurrentHashMap<>();

    private ConnectionService service;
    private Socket connectionSocket;
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;

    private final String ipAddress;
    private final int port;
    private String emailPlainText, emailEncryptedText;
    private String passwordPlainText, passwordEncryptedText;

    protected ConnectionThread(ConnectionService service, String ipAddress, int port, String emailPlainText, String passwordPlainText){
        this.service = service;
        this.ipAddress = ipAddress;
        this.port = port;
        this.emailPlainText = emailPlainText;
        this.passwordPlainText = passwordPlainText;
    }

    public AtomicBoolean isRunning(){
        return isRunning;
    }

    public AtomicBoolean isConnected(){
        return isConnected;
    }

    public ConnectionService getParentService(){
        synchronized (serviceLock){
            return service;
        }
    }

    public Socket getConnectionSocket(){
        synchronized (socketLock){
            return connectionSocket;
        }
    }

    public ObjectInputStream getInputStream(){
        synchronized (inputStreamLock){
            return inputStream;
        }
    }

    public ObjectOutputStream getOutputStream(){
        synchronized (outputStreamLock){
            return outputStream;
        }
    }

    public boolean incomingStartsWith(String prefix, Object incomingStream){
        return (((String) incomingStream).startsWith(prefix));
    }

    public ServerMessage getIncomingMessage(String prefix, Object incomingStream){
        String data = ((String) incomingStream).split(prefix)[1];

        return new GsonBuilder().registerTypeHierarchyAdapter(byte[].class, new ByteArrayAdapter()).create().fromJson(data, ServerMessage.class);

        //TODO: Add to hashmap
    }

    public void sendOutgoingMessage(String prefix, Object outgoingData) throws IOException {

        ClientMessage clientMessage = new ClientMessage(UUID.randomUUID().toString(), outgoingData);
        String outgoingJson = new GsonBuilder().registerTypeHierarchyAdapter(byte[].class, new ByteArrayAdapter()).create().toJson(clientMessage);

        getOutputStream().writeObject(prefix + outgoingJson);
        getOutputStream().flush();

        //TODO: Add to hashmap
    }

    public void run(){
        isRunning.set(true);

        synchronized (socketLock) {
            connectionSocket = new Socket();
        }

        try {
            getConnectionSocket().connect(new InetSocketAddress(ipAddress, port), weMessage.CONNECTION_TIMEOUT_WAIT * 1000);

            synchronized (inputStreamLock) {
                inputStream = new ObjectInputStream(getConnectionSocket().getInputStream());
            }
            synchronized (outputStreamLock) {
                outputStream = new ObjectOutputStream(getConnectionSocket().getOutputStream());
            }
        }catch(SocketTimeoutException ex){
            if (isRunning.get()) {
                sendLocalBroadcast(weMessage.INTENT_LOGIN_TIMEOUT, null);
                getParentService().endService();
            }
            return;
        }catch(IOException ex){
            if (isRunning.get()) {
                AppLogger.error(TAG, "An error occurred while connecting to the weServer.", ex);
                sendLocalBroadcast(weMessage.INTENT_LOGIN_ERROR, null);
                getParentService().endService();
            }
            return;
        }

        while (isRunning.get() && !hasTriedAuthenticating.get()){
            try {
                if (incomingStartsWith(weMessage.JSON_VERIFY_PASSWORD_SECRET, getInputStream().readObject())){
                    ServerMessage message = getIncomingMessage(weMessage.JSON_VERIFY_PASSWORD_SECRET, getInputStream().readObject());
                    JSONEncryptedText secretEncrypted = (JSONEncryptedText) message.getOutgoing();

                    DecryptionTask secretDecryptionTask = new DecryptionTask(new KeyTextPair(secretEncrypted.getEncryptedText(), secretEncrypted.getKey()), CryptoType.BCRYPT);
                    EncryptionTask emailEncryptionTask = new EncryptionTask(emailPlainText, null, CryptoType.AES);

                    secretDecryptionTask.runDecryptTask();
                    emailEncryptionTask.runEncryptTask();

                    String secretString = secretDecryptionTask.getDecryptedText();

                    //TODO: Later on store this in shared preferences <--- so one does not have to reconnect every time
                    EncryptionTask hashPasswordTask = new EncryptionTask(passwordPlainText, secretString, CryptoType.BCRYPT);
                    hashPasswordTask.runEncryptTask();

                    EncryptionTask encryptHashedPasswordTask = new EncryptionTask(hashPasswordTask.getEncryptedText().getEncryptedText(), null, CryptoType.AES);
                    encryptHashedPasswordTask.runEncryptTask();

                    KeyTextPair encryptedEmail = emailEncryptionTask.getEncryptedText();
                    KeyTextPair encryptedHashedPassword = encryptHashedPasswordTask.getEncryptedText();

                    InitConnect initConnect = new InitConnect(
                            weMessage.WEMESSAGE_BUILD_VERSION,
                            Settings.Secure.getString(getParentService().getContentResolver(), Settings.Secure.ANDROID_ID),
                            KeyTextPair.toEncryptedJSON(encryptedEmail),
                            KeyTextPair.toEncryptedJSON(encryptedHashedPassword),
                            DeviceType.ANDROID.getTypeName()
                    );

                    sendOutgoingMessage(weMessage.JSON_INIT_CONNECT, initConnect);
                    hasTriedAuthenticating.set(true);
                }
            }catch(Exception ex){
                AppLogger.error(TAG, "An error occurred while authenticating login information", ex);

                Bundle extras = new Bundle();
                extras.putString(weMessage.BUNDLE_DISCONNECT_REASON_ALTERNATE_MESSAGE, getParentService().getString(R.string.connection_error_authentication_message));
                sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_ERROR, extras);
                getParentService().endService();
                return;
            }
        }

        while (isRunning.get()){
            try {
                if (incomingStartsWith(weMessage.JSON_CONNECTION_TERMINATED, getInputStream().readObject())){
                    ServerMessage serverMessage = getIncomingMessage(weMessage.JSON_CONNECTION_TERMINATED, getInputStream().readObject());
                    DisconnectReason disconnectReason = DisconnectReason.fromCode(((Integer)serverMessage.getOutgoing()));

                    if (disconnectReason == null){
                        AppLogger.error(TAG, "A null disconnect reason has caused the connection to be dropped", new NullPointerException());
                        Bundle extras = new Bundle();
                        extras.putString(weMessage.BUNDLE_DISCONNECT_REASON_ALTERNATE_MESSAGE, getParentService().getString(R.string.connection_error_unknown_message));
                        sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_ERROR, extras);
                        getParentService().endService();
                        return;
                    }

                    switch (disconnectReason){
                        case ALREADY_CONNECTED:
                            sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_ALREADY_CONNECTED, null);
                            getParentService().endService();
                            break;
                        case INVALID_LOGIN:
                            sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_INVALID_LOGIN, null);
                            getParentService().endService();
                            break;
                        case SERVER_CLOSED:
                            sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_SERVER_CLOSED, null);
                            getParentService().endService();
                            break;
                        case ERROR:
                            Bundle extras = new Bundle();
                            extras.putString(weMessage.BUNDLE_DISCONNECT_REASON_ALTERNATE_MESSAGE, getParentService().getString(R.string.connection_error_server_side_message));
                            sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_ERROR, extras);
                            getParentService().endService();
                            break;
                        case FORCED:
                            sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_FORCED, null);
                            getParentService().endService();
                            break;
                        case CLIENT_DISCONNECTED:
                            sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_CLIENT_DISCONNECTED, null);
                            getParentService().endService();
                            break;
                        case INCORRECT_VERSION:
                            sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_INCORRECT_VERSION, null);
                            getParentService().endService();
                            break;
                    }
                    return;
                }

                if (!isConnected.get()){
                    Thread.sleep(3000);

                    sendLocalBroadcast(weMessage.BROADCAST_LOGIN_SUCCESSFUL, null);
                    isConnected.set(true);
                }

            }catch(Exception ex){
                Bundle extras = new Bundle();
                extras.putString(weMessage.BUNDLE_DISCONNECT_REASON_ALTERNATE_MESSAGE, getParentService().getString(R.string.connection_error_unknown_message));
                sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_ERROR, extras);
                AppLogger.error(TAG, "An unknown error occurred. Dropping connection to weServer", ex);
                getParentService().endService();
            }
        }
    }

    protected void endConnection(){
        if (isRunning.get()) {
            isRunning.set(false);

            try {
                sendOutgoingMessage(weMessage.JSON_CONNECTION_TERMINATED, DisconnectReason.CLIENT_DISCONNECTED.getCode());
            }catch(Exception ex){
                AppLogger.error(TAG, "An error occurred while sending disconnect message to the server.", ex);
            }
            try {
                if (isConnected.get()) {
                    isConnected.set(false);
                    getInputStream().close();
                    getOutputStream().close();
                }
                getConnectionSocket().close();
            } catch (Exception ex) {
                AppLogger.error(TAG, "An error occurred while terminating the connection to the weServer.", ex);
                interrupt();
            }
            interrupt();
        }
    }

    private void sendLocalBroadcast(String action, Bundle extras){
        Intent timeoutIntent = new Intent(action);

        if (extras != null) {
            timeoutIntent.putExtras(extras);
        }
        LocalBroadcastManager.getInstance(getParentService()).sendBroadcast(timeoutIntent);
    }
}