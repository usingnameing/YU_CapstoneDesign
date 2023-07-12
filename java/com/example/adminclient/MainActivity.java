package com.example.adminclient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MainActivity extends AppCompatActivity {

    // UI 컴포넌트들을 멤버 변수로 선언합니다.
    private TextView raspberryClient1;
    private TextView raspberryClient2;
    private EditText client1_result;
    private EditText client2_result;

    private RadioGroup radioGroup;
    private Button measureButton;
    private Button resetButton;
    private Socket socket = null;
    private OutputStream outputStream=null;
    private InputStream inputStream=null;
    private Thread sendThread;
    private Thread receiveThread;
    private volatile boolean running = true;  // 송수신 스레드 동작 플래그

    private Queue<String> commandQueue = new ConcurrentLinkedQueue<>();



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI 컴포넌트들을 초기화합니다.
        radioGroup=(RadioGroup)findViewById(R.id.radioGroup);
        raspberryClient1=findViewById(R.id.client1);
        raspberryClient2=findViewById(R.id.client2);
        client1_result = findViewById(R.id.result1);
        client2_result = findViewById(R.id.result2);
        measureButton = findViewById(R.id.measureButton);
        resetButton = findViewById(R.id.resetButton);

        //measureButton.setEnabled(false);
        connectToServer();
        //측정버튼
        measureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("MainActivity", "getMeasureResultFromServer called with command: ");
                commandQueue.add("measure");
                //getMeasureResultFromServer("measure");
            }
        });

        // 화장지 기준량 초기화 버튼
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int selectedId = radioGroup.getCheckedRadioButtonId();

                // 선택된 라디오 버튼에 따라 작업을 수행합니다.
                switch (selectedId) {
                    case R.id.radioId1:
                        // 첫 번째 라디오 버튼이 선택된 경우
                        commandQueue.add("client1:reset");
                        //sendResetTcpSocket("client1","reset");
                        //break;
                    case R.id.radioId2:
                        // 두 번째 라디오 버튼이 선택된 경우
                        commandQueue.add("client2:reset");
                        //break;
                    // 추가적인 라디오 버튼이 있다면 여기에 추가합니다.
                    default:
                        // 선택된 라디오 버튼이 없는 경우
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle("알림");
                        builder.setMessage("초기화할 곳을 선택해주세요.");
                        builder.setPositiveButton("확인", null);
                        AlertDialog alertDialog = builder.create();
                        alertDialog.show();
                        break;
                }
            }
        });


    }
    private void connectToServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 서버의 IP 주소와 포트 번호를 지정합니다.
                    String serverIp = "192.168.0.17";
                    int serverPort = 8080;

                    // TCP 소켓을 생성하여 서버에 연결합니다.
                    socket = new Socket(serverIp, serverPort);
                    outputStream=socket.getOutputStream();
                    inputStream=socket.getInputStream();

                    commandQueue.add("admin");
                    sendThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            while(running){
                                while (!Thread.currentThread().isInterrupted()) {
                                    if (Thread.currentThread().isInterrupted()) {
                                        break;
                                    }

                                    if (!commandQueue.isEmpty()) {
                                        try {
                                            String command = commandQueue.poll();
                                            byte[] dataBytes = command.getBytes("UTF-8");
                                            int dataLength = dataBytes.length;

                                            // 리틀엔디안 방식으로 바이트 변환
                                            ByteBuffer bb = ByteBuffer.allocate(4);
                                            bb.order(ByteOrder.LITTLE_ENDIAN);
                                            bb.putInt(dataLength);
                                            byte[] lengthBytes = bb.array();

                                            outputStream.write(dataBytes);
                                            outputStream.flush();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }
                        }
                    });

                    receiveThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            // 여기에 메세지를 수신하는 코드를 작성합니다.
                            // 'running' 플래그를 확인하여 스레드를 정지할 수 있게 합니다.
                            while (running) {
                                // 메세지 수신...
                                while (!Thread.currentThread().isInterrupted()) {
                                    if (Thread.currentThread().isInterrupted()) {
                                        break;
                                    }

                                    try {
                                        // Read the length of the incoming message, first 4 bytes.
                                        byte[] lengthBytes = new byte[4];
                                        if (inputStream.read(lengthBytes) == -1)
                                            break; // End of stream reached

                                        // Since we are receiving the length as little endian, we convert it to the big endian which is the default byte order in Java.
                                        int messageLength = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

                                        // Read the incoming message. 'messageLength' informs us how many bytes we need to read.
                                        byte[] buffer = new byte[messageLength];
                                        inputStream.read(buffer);
                                        String message = new String(buffer, 0, messageLength, "UTF-8");
                                        Log.d("Data received ","Received Data: "+message);
                                        // Convert the message bytes to String
                                        String[] state=message.split(":");

                                        // Here, we assume the message is in the format "clientId:result", as per your previous code

                                        final String clientId = state[0].trim();
                                        final String result = state[1].trim();

                                        Log.d("Data received","clientID: "+clientId);
                                        Log.d("Data received","result: "+result);
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {

                                                    // Check which client's EditText needs to be updated
                                                    if (clientId.equals("client1")) {
                                                        if(result.startsWith("No")){
                                                            client1_result.setText(result);
                                                        } else if (result.startsWith("Problem")) {
                                                            client1_result.setText(result);
                                                        }
                                                        else if(result.startsWith("reset")){
                                                            client1_result.setText(result);
                                                        }
                                                        else{
                                                            client1_result.setText(result+"%");
                                                        }

                                                    } else if (clientId.equals("client2")) {
                                                        if(result.startsWith("No")){
                                                            client2_result.setText(result);
                                                        } else if (result.startsWith("Problem")) {
                                                            client2_result.setText(result);
                                                        }
                                                        client2_result.setText(result+"%");
                                                    } else {
                                                        Log.e("TAG", "Unknown client ID: " + clientId);
                                                    }

                                            }
                                        });

                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }

                    });
                    sendThread.start();
                    receiveThread.start();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        running = false;  // 앱이 종료될 때 스레드를 정지시킵니다.
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
