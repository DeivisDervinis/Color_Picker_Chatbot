package com.example.vmac.WatBot;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.ibm.cloud.sdk.core.http.HttpMediaType;
import com.ibm.cloud.sdk.core.http.Response;
import com.ibm.cloud.sdk.core.http.ServiceCall;
import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.watson.assistant.v2.model.DialogNodeOutputOptionsElement;
import com.ibm.watson.assistant.v2.model.MessageContext;
import com.ibm.watson.assistant.v2.model.RuntimeEntity;
import com.ibm.watson.assistant.v2.model.RuntimeResponseGeneric;
import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneHelper;
import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneInputStream;
import com.ibm.watson.developer_cloud.android.library.audio.StreamPlayer;
import com.ibm.watson.developer_cloud.android.library.audio.utils.ContentType;
import com.ibm.watson.assistant.v2.Assistant;
import com.ibm.watson.assistant.v2.model.CreateSessionOptions;
import com.ibm.watson.assistant.v2.model.MessageInput;
import com.ibm.watson.assistant.v2.model.MessageOptions;
import com.ibm.watson.assistant.v2.model.MessageResponse;
import com.ibm.watson.assistant.v2.model.SessionResponse;
import com.ibm.watson.speech_to_text.v1.SpeechToText;
import com.ibm.watson.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.speech_to_text.v1.model.SpeechRecognitionResults;
import com.ibm.watson.speech_to_text.v1.websocket.BaseRecognizeCallback;
import com.ibm.watson.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.text_to_speech.v1.model.SynthesizeOptions;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {


    private RecyclerView recyclerView;
    private ChatAdapter mAdapter;
    private ArrayList messageArrayList;
    private EditText inputMessage;
    private ImageButton btnSend;
    private ImageButton btnRecord;
    StreamPlayer streamPlayer = new StreamPlayer();
    private boolean initialRequest;
    private boolean permissionToRecordAccepted = false;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static String TAG = "MainActivity";
    private static final int RECORD_REQUEST_CODE = 101;
    private boolean listening = false;
    private MicrophoneInputStream capture;
    private Context mContext;
    private MicrophoneHelper microphoneHelper;

    private Assistant watsonAssistant;
    private Response<SessionResponse> watsonAssistantSession;
    private SpeechToText speechService;
    private TextToSpeech textToSpeech;

    private String colorStringMessage = "";
    private TextView mess;
    private LinearLayoutManager layout;

    int colorAltGlobal;

    private void createServices() {
        watsonAssistant = new Assistant("2019-02-28", new IamAuthenticator(mContext.getString(R.string.assistant_apikey)));
        watsonAssistant.setServiceUrl(mContext.getString(R.string.assistant_url));

        textToSpeech = new TextToSpeech(new IamAuthenticator((mContext.getString(R.string.TTS_apikey))));
        textToSpeech.setServiceUrl(mContext.getString(R.string.TTS_url));

        speechService = new SpeechToText(new IamAuthenticator(mContext.getString(R.string.STT_apikey)));
        speechService.setServiceUrl(mContext.getString(R.string.STT_url));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = getApplicationContext();

        inputMessage = findViewById(R.id.message);
        btnSend = findViewById(R.id.btn_send);
        btnRecord = findViewById(R.id.btn_record);
        String customFont = "Montserrat-Regular.ttf";
        Typeface typeface = Typeface.createFromAsset(getAssets(), customFont);
        inputMessage.setTypeface(typeface);
        recyclerView = findViewById(R.id.recycler_view);

        messageArrayList = new ArrayList<>();
        mAdapter = new ChatAdapter(messageArrayList);
        microphoneHelper = new MicrophoneHelper(this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(mAdapter);
        this.inputMessage.setText("");
        this.initialRequest = true;

        //mess = findViewById(R.id)

        int permission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission to record denied");
            makeRequest();
        } else {
            Log.i(TAG, "Permission to record was already granted");
        }


        recyclerView.addOnItemTouchListener(new RecyclerTouchListener(getApplicationContext(), recyclerView, new ClickListener() {
            @Override
            public void onClick(View view, final int position) {
                Message audioMessage = (Message) messageArrayList.get(position);
                if (audioMessage != null && !audioMessage.getMessage().isEmpty()) {
                    new SayTask().execute(audioMessage.getMessage());
                }
            }

            @Override
            public void onLongClick(View view, int position) {
                recordMessage();

            }
        }));

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkInternetConnection()) {
                    sendMessage();
                }
            }
        });

        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordMessage();
            }
        });

        createServices();
        sendMessage();
    }

    ;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.refresh:
                finish();
                startActivity(getIntent());

        }
        return (super.onOptionsItemSelected(item));
    }


    // Speech-to-Text Record Audio permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        try {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            switch (requestCode) {
                case REQUEST_RECORD_AUDIO_PERMISSION:
                    permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    break;
                case RECORD_REQUEST_CODE: {

                    if (grantResults.length == 0
                            || grantResults[0] !=
                            PackageManager.PERMISSION_GRANTED) {

                        Log.i(TAG, "Permission has been denied by user");
                    } else {
                        Log.i(TAG, "Permission has been granted by user");
                    }
                    return;
                }

                case MicrophoneHelper.REQUEST_PERMISSION: {
                    if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Permission to record audio denied", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
        catch(Exception e){
            Log.d("Service", "Service offline");
        }
        // if (!permissionToRecordAccepted ) finish();

    }

    protected void makeRequest() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                MicrophoneHelper.REQUEST_PERMISSION);
    }

    // Methid that updates the design
    private void updateDesign(String colorHex, String colorAltHex){
        // Sets the primary color of the design
        int color = Color.parseColor(colorHex);

        // Sets the secondary color of the design
        int colorAlt = Color.parseColor(colorAltHex);

        // Sets the color for recognition of the design
        colorAltGlobal = colorAlt;

        // Changes the action bar to the primary color
        getSupportActionBar().setBackgroundDrawable(new ColorDrawable(color));

        // Changes the background of the app to the secondary color
        recyclerView.setBackgroundColor(colorAlt);
    }


    // Sending a message to Watson Assistant Service
    private void sendMessage() {
        final String inputmessage = this.inputMessage.getText().toString().trim();

        if (!this.initialRequest) {
            Message inputMessage = new Message();
            inputMessage.setMessage(inputmessage);
            inputMessage.setId("1");
            messageArrayList.add(inputMessage);
        } else {
            Message inputMessage = new Message();
            inputMessage.setMessage(inputmessage);
            inputMessage.setId("100");
            this.initialRequest = false;
            Toast.makeText(getApplicationContext(), "Tap on the message for Voice", Toast.LENGTH_LONG).show();

        }

        // Method that allows the color picker to happen || Checks if user wishes to apply the message and if the colorStringMessage contains a color
        if (inputmessage.equalsIgnoreCase("apply") && colorStringMessage.length() != 0) {

            // Checks for the selected primary color
            if(colorStringMessage.equalsIgnoreCase("blue") || colorAltGlobal == Color.parseColor("#ffaf59")){
                if(colorStringMessage.equalsIgnoreCase("orange")){
                    Log.d("Color String Message", colorStringMessage);
                    updateDesign("#325ca8", "#ffaf59");
                }
                else {
                    updateDesign("#325ca8", "#70aeff");
                }

            }
            else if(colorStringMessage.equalsIgnoreCase("green")){
                updateDesign("#79f28d", "#8fffce");
            }
            else if(colorStringMessage.equalsIgnoreCase("red")) {
                updateDesign("#ff4d4d", "#ffa7a6");
            }
            else if(colorStringMessage.equalsIgnoreCase("orange")){
                updateDesign("#325ca8", "#ffaf59");
            }

            // Same concept for changing the view to the other different colors

        }
        this.inputMessage.setText("");
        mAdapter.notifyDataSetChanged();

        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    if (watsonAssistantSession == null) {
                        ServiceCall<SessionResponse> call = watsonAssistant.createSession(new CreateSessionOptions.Builder().assistantId(mContext.getString(R.string.assistant_id)).build());
                        watsonAssistantSession = call.execute();
                    }

                    MessageInput input = new MessageInput.Builder()
                            .text(inputmessage)
                            .build();
                    MessageOptions options = new MessageOptions.Builder()
                            .assistantId(mContext.getString(R.string.assistant_id))
                            .input(input)
                            .sessionId(watsonAssistantSession.getResult().getSessionId())
                            .build();
                    Response<MessageResponse> response = watsonAssistant.message(options).execute();
                    List<RuntimeEntity> responseString = response.getResult().getOutput().getEntities();

                    // Checks if the entity exists
                    if(responseString.size() > 0) {
                        //Log.i(TAG, "Value " + responseString.get(0).value());
                        Log.d("RESPONSE DEBUG", response.getResult().toString());
                        colorStringMessage = responseString.get(0).value();
                    }

                    if (response != null &&
                            response.getResult().getOutput() != null &&
                            !response.getResult().getOutput().getGeneric().isEmpty()) {

                        List<RuntimeResponseGeneric> responses = response.getResult().getOutput().getGeneric();

                        for (RuntimeResponseGeneric r : responses) {
                            Message outMessage;
                            switch (r.responseType()) {
                                case "text":
                                    outMessage = new Message();
                                    outMessage.setMessage(r.text());
                                    outMessage.setId("2");

                                    messageArrayList.add(outMessage);

                                    // speak the message
                                    new SayTask().execute(outMessage.getMessage());
                                    break;

                                case "option":
                                    outMessage = new Message();
                                    String title = r.title();
                                    String OptionsOutput = "";
                                    for (int i = 0; i < r.options().size(); i++) {
                                        DialogNodeOutputOptionsElement option = r.options().get(i);
                                        OptionsOutput = OptionsOutput + option.getLabel() + "\n";

                                    }
                                    outMessage.setMessage(title + "\n" + OptionsOutput);
                                    outMessage.setId("2");

                                    messageArrayList.add(outMessage);

                                    // speak the message
                                    new SayTask().execute(outMessage.getMessage());
                                    break;

                                case "image":
                                    outMessage = new Message(r);
                                    messageArrayList.add(outMessage);

                                    // speak the description
                                    new SayTask().execute("You received an image: " + outMessage.getTitle() + outMessage.getDescription());
                                    break;
                                default:
                                    Log.e("Error", "Unhandled message type");
                            }
                        }

                        runOnUiThread(new Runnable() {
                            public void run() {
                                mAdapter.notifyDataSetChanged();
                                if (mAdapter.getItemCount() > 1) {
                                    recyclerView.getLayoutManager().smoothScrollToPosition(recyclerView, null, mAdapter.getItemCount() - 1);

                                }

                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();
    }


    //Record a message via Watson Speech to Text
    private void recordMessage() {
        if (listening != true) {
            capture = microphoneHelper.getInputStream(true);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        speechService.recognizeUsingWebSocket(getRecognizeOptions(capture), new MicrophoneRecognizeDelegate());
                    } catch (Exception e) {
                        showError(e);
                    }
                }
            }).start();
            listening = true;
            Toast.makeText(MainActivity.this, "Listening....Click to Stop", Toast.LENGTH_LONG).show();

        } else {
            try {
                microphoneHelper.closeInputStream();
                listening = false;
                Toast.makeText(MainActivity.this, "Stopped Listening....Click to Start", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * Check Internet Connection
     *
     * @return
     */
    private boolean checkInternetConnection() {
        // get Connectivity Manager object to check connection
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();

        // Check for network connections
        if (isConnected) {
            return true;
        } else {
            Toast.makeText(this, " No Internet Connection available ", Toast.LENGTH_LONG).show();
            return false;
        }

    }

    //Private Methods - Speech to Text
    private RecognizeOptions getRecognizeOptions(InputStream audio) {
        return new RecognizeOptions.Builder()
                .audio(audio)
                .contentType(ContentType.OPUS.toString())
                .model("en-US_BroadbandModel")
                .interimResults(true)
                .inactivityTimeout(2000)
                .build();
    }

    private void showMicText(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                inputMessage.setText(text);
            }
        });
    }

    private void enableMicButton() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnRecord.setEnabled(true);
            }
        });
    }

    private void showError(final Exception e) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        });
    }


    private class SayTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            try{

            streamPlayer.playStream(textToSpeech.synthesize(new SynthesizeOptions.Builder()
                    .text(params[0])
                    .voice(SynthesizeOptions.Voice.EN_US_LISAVOICE)
                    .accept(HttpMediaType.AUDIO_WAV)
                    .build()).execute().getResult());
            return "Did synthesize";
            }
            catch(Exception e){
                return "Did not synthesize";
            }
        }
    }

    //Watson Speech to Text Methods.
    private class MicrophoneRecognizeDelegate extends BaseRecognizeCallback {
        @Override
        public void onTranscription(SpeechRecognitionResults speechResults) {
            try {
                if (speechResults.getResults() != null && !speechResults.getResults().isEmpty()) {
                    String text = speechResults.getResults().get(0).getAlternatives().get(0).getTranscript();
                    showMicText(text);
                }
            }
            catch(Exception e){
                Log.d("Service Speech to Text", "Service unavailable");
            }
        }

        @Override
        public void onError(Exception e) {
            showError(e);
            enableMicButton();
        }

        @Override
        public void onDisconnected() {
            enableMicButton();
        }

    }


}



