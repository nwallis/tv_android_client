package com.example.nathanwallis.android_prototype;

import com.example.nathanwallis.android_prototype.util.Utils;

import android.app.Activity;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.VideoView;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.phoenixframework.channels.Channel;
import org.phoenixframework.channels.Envelope;
import org.phoenixframework.channels.IErrorCallback;
import org.phoenixframework.channels.IMessageCallback;
import org.phoenixframework.channels.ISocketCloseCallback;
import org.phoenixframework.channels.ISocketOpenCallback;
import org.phoenixframework.channels.ITimeoutCallback;
import org.phoenixframework.channels.Socket;

import java.io.IOException;
import java.util.Date;

public class ChatActivity extends Activity {
    private static final String TAG = ChatActivity.class.getSimpleName();

    private EditText messageField;
    private Socket socket;
    private Channel channel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause(){
        super.onPause();
        sendStatus("client:disconnect");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        final Button button = findViewById(R.id.sendPing);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendStatus("button:clicked");
            }
        });

        final Utils utils = new Utils(getApplicationContext());
        final String url = utils.getUrl();
        final String topic = utils.getTopic();

        try {
            socket = new Socket(url.toString());
            socket.reconectOnFailure(false);

            socket.onOpen(new ISocketOpenCallback() {
                @Override
                public void onOpen() {
                    showToast("Connected");
                    channel = socket.chan(topic, null);

                    try {
                        channel.join().receive("ok", new IMessageCallback() {
                            @Override
                            public void onMessage(final Envelope envelope) {
                                showToast("You have joined '" + topic + "'");
                                sendStatus("fridge:connect");
                            }
                        });
                        channel.on("user:entered", new IMessageCallback() {
                            @Override
                            public void onMessage(final Envelope envelope) {
                                final JsonNode user = envelope.getPayload().get("user");
                                if (user == null || user instanceof NullNode) {
                                    showToast("An anonymous user entered");
                                }
                                else {
                                    showToast("User '" + user.toString() + "' entered");
                                }
                            }
                        }).on("new:msg", new IMessageCallback() {
                            @Override
                            public void onMessage(final Envelope envelope) {
                                final ChatMessage message;
                                try {
                                    message = objectMapper.treeToValue(envelope.getPayload(), ChatMessage.class);
                                    Log.i(TAG, "MESSAGE: " + message);
                                    if (message.getUserId() != null && !message.getUserId().equals("SYSTEM")) {
                                        notifyMessageReceived();
                                    }
                                } catch (JsonProcessingException e) {
                                    Log.e(TAG, "Unable to parse message", e);
                                }
                            }
                        }).on("video:change", new IMessageCallback() {
                            @Override
                            public void onMessage(final Envelope envelope) {
                                final JsonNode video_name = envelope.getPayload().get("video_name");
                                final String video_name_string = video_name.toString().replaceAll("\"", "");
                                showToast("changing video to -> " + video_name_string);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        VideoView videoview = (VideoView) findViewById(R.id.videoView);
                                        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/raw/" + video_name_string);
                                        //Uri uri = Uri.parse("android.resource://"+getPackageName()+"/"+R.raw.legend);
                                        videoview.setVideoURI(uri);
                                        videoview.start();
                                    }
                                });
                            }
                        }).on("ping:ping", new IMessageCallback() {
                            @Override
                            public void onMessage(final Envelope envelope) {
                                sendStatus("pinged:pinged");
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to join channel " + topic, e);
                        handleTerminalError(e);
                    }
                }
            })
                    .onClose(new ISocketCloseCallback() {
                        @Override
                        public void onClose() {
                            showToast("Closed");
                        }
                    })
                    .onError(new IErrorCallback() {
                        @Override
                        public void onError(final String reason) {
                            handleTerminalError(reason);
                        }
                    })
                    .connect();

        } catch (Exception e) {
            Log.e(TAG, "Failed to connect", e);
            handleTerminalError(e);
        }
    }

    private void changeVideo() {


    }

    private void sendStatus(final String status){
        //sends a status with an empty payload
        if (channel != null) {
            final ObjectNode payload = objectMapper.createObjectNode();
            try {
                channel.push(status, payload);
            } catch (IOException e) {
                Log.e(TAG, "Failed to send", e);
                showToast("Failed to send");
            }
        }
    }

    /*private void sendMessage() {
        final String messageBody = messageField.getText().toString().trim();
        if (channel != null) {
            final ObjectNode payload = objectMapper.createObjectNode();
            payload.put("body", messageBody);
            try {
                final Date pushDate = new Date();
                channel.push("new:msg", payload)
                        .receive("ok", new IMessageCallback() {
                            @Override
                            public void onMessage(final Envelope envelope) {
                                final ChatMessage message = new ChatMessage();
                                message.setBody(messageBody);
                                message.setInsertedDate(pushDate);
                                message.setFromMe(true);
                                Log.i(TAG, "MESSAGE: " + message);
                                addToList(message);
                            }
                        })
                        .timeout(new ITimeoutCallback() {
                            @Override
                            public void onTimeout() {
                                Log.w(TAG, "MESSAGE timed out");
                            }
                        });
            } catch (IOException e) {
                Log.e(TAG, "Failed to send", e);
                showToast("Failed to send");
            }
        }
    }*/

    private void showToast(final String toastText) {
        Log.i(TAG, "BFA MESSAGE: " + toastText);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast toast = Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
            }
        });
    }

    private void notifyMessageReceived() {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleTerminalError(final Throwable t) {
        handleTerminalError(t.toString());
    }

    private void handleTerminalError(final String s) {
        showToast(s);
    }

}
