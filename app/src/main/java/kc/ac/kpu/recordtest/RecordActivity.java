package kc.ac.kpu.recordtest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.Manifest;
import android.content.Intent;
import android.media.AudioFormat;
import android.net.Uri;
import android.os.Environment;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;


import androidx.annotation.NonNull;

public class RecordActivity extends AppCompatActivity {

    /*@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.record_activity);
    }*/

    String[] PERMISSION = {Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS};
    private static final String TAG = "VoiceChangerSample";

    private static final int SAMPLE_RATE = 8000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    private static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private MicRecordTask recordTask;
    private AudioPlayTask playTask;
    private AlertDialog saveDialog;

    private WaveDisplayView displayView;
    private ProgressBar progressBar;
    //잠시 수정
    //private BootstrapMethodError recordButton, playButton, stopButton, saveButton;
    private Button recordButton, playButton, stopButton, saveButton;
    private TextView countText;

    private Object recordNumber;
    private int labelNumber;
    private FirebaseAuth FirebaseAuth;
    private FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    private DatabaseReference mdatabase = FirebaseDatabase.getInstance().getReference();

    //private StorageReference mStorageRef;
    //mStorageRef = FirebaseStorage.getInstance().getReference();

    private FirebaseStorage storage = FirebaseStorage.getInstance();
    private StorageReference storageRef = storage.getReference();


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "Start.");
        LinearLayout displayLayout = (LinearLayout) findViewById(R.id.displayView);
        displayView = new WaveDisplayView(getBaseContext());
        displayLayout.addView(displayView);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        recordButton = (Button) findViewById(R.id.Record);
        playButton = (Button) findViewById(R.id.Play);
        stopButton = (Button) findViewById(R.id.Stop);
        saveButton = (Button) findViewById(R.id.Save);

        configureEventListener();
        //setInitializeState();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean ret = super.onCreateOptionsMenu(menu);
        int index = Menu.FIRST;
        menu.add(Menu.NONE, index++, Menu.NONE, "データクリア");
        menu.add(Menu.NONE, index++, Menu.NONE, "ノイズを追加");
        menu.add(Menu.NONE, index++, Menu.NONE, "サインを追加");
        menu.add(Menu.NONE, index++, Menu.NONE, "矩形を追加");
        return ret;
    }

    /*
    @Override
      public boolean onOptionsItemSelected(MenuItem item) {
        final int size = 8000;
        final int freq = 440;
        switch (item.getItemId()) {
            case Menu.FIRST:
                displayView.clearWaveData();
                break;
            case Menu.FIRST + 1:
                displayView.addWaveData(NormalizeWaveData.createNoiseData(size));
                break;
            case Menu.FIRST + 2:
                displayView.addWaveData(NormalizeWaveData.createSineData(size, freq));
                break;
            case Menu.FIRST + 3:
                displayView.addWaveData(NormalizeWaveData.createSquareData(size, freq));
                break;
        }
        return true;
    }*/

    @Override
    protected void onPause() {
        if (stopButton.isEnabled()) {
            stopAll();
        }
        super.onPause();
    }

    private void configureEventListener() {
        recordButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startRecording();
            }
        }); //여기원래 잇는 부분인데 configureEvent listener가 어디에 있찌?

        playButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startPlaying();
            }
        });

        stopButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAll();
            }
        });

        /*saveDialog = createSaveDialog();
        saveButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                saveDialog.show();
            }
        });*/


    }

    /*private void setInitializeState() {
        recordButton.setEnabled(true);
        playButton.setEnabled(true);
        stopButton.setEnabled(false);
        saveButton.setEnabled(true);
    }*/

    /*private AlertDialog createSaveDialog() {
        final Handler handler = new Handler();
        final View view = LayoutInflater.from(this).inflate(R.layout.save_dialog, null);
        return new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_save_title)
                .setView(view)
                .setPositiveButton(R.string.dialog_save_button_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        EditText filename = (EditText) view.findViewById(R.id.filenameEditText);
                        RadioButton wavRadio = (RadioButton) view.findViewById(R.id.wavRadio);

                        boolean isWavFile = wavRadio.isChecked();
                        final File file = new File(getSavePath(), filename.getText() + (isWavFile ? ".wav" : ".raw"));
                        saveSoundFile(file, isWavFile);

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(RecorderActivity.this, "Save completed: " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .setNegativeButton(R.string.dialog_save_button_cancel, null)
                .create();
    }*/

    private boolean saveSoundFile(File savefile, boolean isWavFile) {

        Uri file;
        StorageReference wavRef;
        UploadTask uploadTask;

        byte[] data = displayView.getAllWaveData();
        if (data.length == 0) {
            Log.w(TAG, "save data is not found");
            return false;
        }
        try {
            savefile.createNewFile();
            FileOutputStream targetStream = new FileOutputStream(savefile);
            try {
                if (isWavFile) {
                    WaveFileHeaderCreator.pushWaveHeader(targetStream, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_ENCODING, data.length);
                }
                targetStream.write(data);
            } finally {
                if (targetStream != null) {
                    targetStream.close();
                }
            }
            file = Uri.fromFile(new File(getSavePath() + "/" + String.valueOf(labelNumber) + "-" + recordNumber.toString() + ".wav"));
            wavRef = storageRef.child(user.getUid() + "/learning/" + file.getLastPathSegment());
            uploadTask = wavRef.putFile(file);
            /*uploadTask.addOnFailureListener (new OnFailureListener(){
                public void onFailure(@NonNull Exception e){
                    Toast.makeText(RecordActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }).addOnSuccessListener((OnSuccessListener)(TaskSnapshot){
                    Toast.makeText(RecordActivity.this,"FileUpload Success", Toast.LENGTH_LONG).show();
            });/*
            return true;

            /* 여기까지 */
            uploadTask.addOnFailureListener(new OnFailureListener() { //upload 수행의 결과를 확인하는 리스너
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(RecordActivity.this, e.getMessage() + "실패", Toast.LENGTH_LONG).show();
                }
            }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Toast.makeText(RecordActivity.this, "FileUpload Success", Toast.LENGTH_SHORT).show();
                }
            });
            return true;


        } catch (IOException ex) {
            Log.w(TAG, "Fail to save sound file", ex);
            return false;
        }
    }

    private void startRecording(){
        Log.i(TAG,"start recording");
        setButtonEnable(true);
        try{
            recordTask=new MicRecordTask(progressBar, displayView, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_ENCODING);
            recordTask.setMax(3 * getDataBytesPerSecond(SAMPLE_RATE,CHANNEL_CONFIG,AUDIO_ENCODING));
        } catch (IllegalArgumentException ex){
            Log.w(TAG, "Fail to create MicRecordTask", ex);
        }
        recordTask.start();
        waitEndTask(recordTask);
    }

    private void stopRecording(){
        stopTask(recordTask);
        FirebaseDatabase.getInstance()
                .getReference()
                .child("users")
                .child(user.getUid())
                .child("recordNumber")
                .addListenerForSingleValueEvent(new ValueEventListener(){
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        recordNumber = dataSnapshot.getValue();
                        labelNumber = Integer.parseInt(recordNumber.toString()) / 50;

                        if (Integer.parseInt(recordNumber.toString()) % 10 == 9)
                            countText.setText(String.valueOf(labelNumber + 1));
                        else
                            countText.setText(String.valueOf(labelNumber));

                        if (Integer.parseInt(recordNumber.toString()) > 498) {
                            mdatabase.child("users").child(user.getUid()).child("learning").setValue("true");
                            mdatabase.child("users").child(user.getUid()).child("NewUser").setValue("No");

                            Intent intent = new Intent(RecordActivity.this, MainActivity.class);
                            startActivity(intent);
                            finish();
                        }

                        Toast.makeText(RecordActivity.this, recordNumber.toString(), Toast.LENGTH_LONG).show();
                        final File file = new File(getSavePath(), String.valueOf(labelNumber) + "_" + recordNumber.toString() + ".wav");

                        saveSoundFile(file, true);

                        recordNumber = Integer.parseInt(recordNumber.toString()) + 1;
                        mdatabase.child("users").child(user.getUid()).child("recordNumber").setValue(recordNumber);
                    }

                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "ERROR DataBase");
                    }

                });
        Log.i(TAG,"stop recording");
    }

    private void startPlaying() {
        Log.i(TAG, "start playing.");

        setButtonEnable(true);
        try {
            playTask = new AudioPlayTask(progressBar, displayView, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_ENCODING);
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Fail to create MicRecordTask.", ex);
        }
        playTask.start();
        waitEndTask(playTask);
    }

    private void stopPlaying() {
        stopTask(playTask);
        Log.i(TAG, "stop playing.");
    }

    private void stopTask(StopableTask task) {
        if (task.stopTask()) {
            try {
                task.join(1000);
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted recoring thread stopping.");
            }
        }
        setButtonEnable(false);
    }

    private void stopAll() {
        if (recordTask != null && recordTask.isRunning()) {
            stopRecording();
        }
        if (playTask != null && playTask.isRunning()) {
            stopPlaying();
        }
    }


    private void setButtonEnable(boolean b) {
        recordButton.setEnabled(!b);
        playButton.setEnabled(!b);
        stopButton.setEnabled(b);
        saveButton.setEnabled(!b && hasSDCard());
    }

    private void waitEndTask(final Thread t){
        final Handler handler = new Handler();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    t.join();
                } catch (InterruptedException e){
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        setButtonEnable(false);
                        stopRecording();
                    }
                });
            }
        }).start();
    }


    private File getSavePath() {
        if (hasSDCard()) {
            File path = new File(Environment.getExternalStorageDirectory(), "/VoiceChanger/");
            path.mkdirs();
            return path;
        } else {
            Log.i(TAG, "SDCard is unuseable: " + Environment.getExternalStorageState());
            return getFilesDir();
        }
    }


    private boolean hasSDCard() {
        String state = Environment.getExternalStorageState();
        return state.equals(Environment.MEDIA_MOUNTED);
    }

    private int getDataBytesPerSecond(int sampleRate, int channelConfig, int audioEncoding) {
        boolean is8bit = audioEncoding == AudioFormat.ENCODING_PCM_8BIT;
        boolean isMonoChannel = channelConfig != AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        return sampleRate * (isMonoChannel ? 1: 2) * (is8bit ? 1: 2);
    }


}
