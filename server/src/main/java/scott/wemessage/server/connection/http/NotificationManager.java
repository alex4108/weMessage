/*
 *  weMessage - iMessage for Android
 *  Copyright (C) 2018 Roman Scott
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package scott.wemessage.server.connection.http;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import scott.wemessage.commons.connection.json.message.JSONNotification;
import scott.wemessage.commons.crypto.AESCrypto;
import scott.wemessage.commons.utils.StringUtils;
import scott.wemessage.server.MessageServer;
import scott.wemessage.server.ServerLogger;
import scott.wemessage.server.messages.Message;
import scott.wemessage.server.messages.chat.GroupChat;
import scott.wemessage.server.weMessage;

public class NotificationManager {

    private final long MINIMUM_INTERVAL = 1500;

    private MessageServer messageServer;
    private AtomicBoolean unsupportedNotificationVersion = new AtomicBoolean(false);
    private AtomicBoolean hasErrored = new AtomicBoolean(false);
    private HashMap<String, Long> lastExecutionMap = new HashMap<>();

    public NotificationManager(MessageServer messageServer){
        this.messageServer = messageServer;
    }

    public void sendNotification(final String registrationToken, final Message message){
        boolean run = false;

        Long previousClickTimestamp = lastExecutionMap.get(registrationToken);
        long currentTimestamp = System.currentTimeMillis();

        lastExecutionMap.put(registrationToken, currentTimestamp);

        if(previousClickTimestamp == null || (currentTimestamp - previousClickTimestamp > MINIMUM_INTERVAL)) {
            run = true;
        }

        if (!run) return;

        if (!StringUtils.isEmpty(registrationToken) && !unsupportedNotificationVersion.get()) {
            new Thread(new Runnable() {
                public void run() {
                    try {
                        if (!messageServer.getConfiguration().getConfigJSON().getConfig().getSendNotifications()) return;

                        String plainText;

                        if (message.getText().length() > weMessage.MAX_NOTIFICATION_CHAR_SIZE + 1){
                            plainText = message.getText().substring(0, weMessage.MAX_NOTIFICATION_CHAR_SIZE) + "...";
                        }else {
                            plainText = message.getText();
                        }

                        String keys = AESCrypto.keysToString(AESCrypto.generateKeys());
                        String encryptedText = AESCrypto.encryptString(plainText, keys);
                        String chatName = "";
                        int attachmentNumber = 0;

                        if (message.getChat() instanceof GroupChat) {
                            if (!StringUtils.isEmpty(((GroupChat) message.getChat()).getDisplayName())) {
                                chatName = ((GroupChat) message.getChat()).getDisplayName();
                            }
                        }

                        if (message.getAttachments() != null){
                            attachmentNumber = message.getAttachments().size();
                        }

                        JSONNotification notification = new JSONNotification(
                                String.valueOf(weMessage.FIREBASE_NOTIFICATION_VERSION),
                                registrationToken,
                                encryptedText,
                                keys,
                                message.getHandle().getHandleID(),
                                message.getChat().getGuid(),
                                chatName,
                                String.valueOf(attachmentNumber),
                                messageServer.getConfiguration().getConfigJSON().getConfig().getAccountInfo().getEmail()
                        );

                        OkHttpClient client = new OkHttpClient();
                        RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), new Gson().toJson(notification));
                        Request request = new Request.Builder().url(weMessage.NOTIFICATION_FUNCTION_URL).post(body).build();

                        Response response = client.newCall(request).execute();

                        if (response.code() == weMessage.HTTP_FIREBASE_GENERIC_ERROR && !hasErrored.get()){
                            ServerLogger.log(ServerLogger.Level.ERROR, "An error occurred while sending a notification. Future notifications may or may not fail to send.");
                            ServerLogger.log(ServerLogger.Level.ERROR, "Restarting your weServer may fix this issue.");
                            hasErrored.set(true);
                        }else if (response.code() == weMessage.HTTP_FIREBASE_UNSUPPORTED_NOTIFICATION_VERSION){
                            ServerLogger.log(ServerLogger.Level.ERROR, "A notification could not be sent because this weServer version lacks support for proper notification handling.");
                            unsupportedNotificationVersion.set(true);
                        }

                        response.close();
                    } catch (Exception ex) {
                        try {
                            ServerLogger.error("An error occurred while trying to send a notification to Device: " +
                                    messageServer.getDatabaseManager().getDeviceIdByName(messageServer.getDatabaseManager().getDeviceIdByRegistrationToken(registrationToken)), ex);
                        }catch (Exception exc){
                            ServerLogger.error("An error occurred while trying to send a notification to a device.", ex);
                        }
                    }
                }
            }).start();
        }
    }
}