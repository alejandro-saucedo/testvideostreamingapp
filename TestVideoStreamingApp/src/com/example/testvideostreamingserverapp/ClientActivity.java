package com.example.testvideostreamingserverapp;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import android.hardware.Camera;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

public class ClientActivity extends Activity implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {
	public static final String TAG = ClientActivity.class.getName();
	public static final int REQ_VIDEO_SIZE = ServerActivity.PACKET_SIZE*100;
	private CameraPreview preview = null;
	private Camera camera = null;
	private String host = null;
	private boolean receiving  = false;
	private FilePathProvider fpp = null;
	private MediaPlayer baseMp = null;
	private File videoFile = null;
	private int lastDuration = 0;
	private long lastFileLength = 0;
	private long delayTime = 50;
	private volatile long currFileLength = 0;
	private SurfaceView surfaceView = null;
	

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);
		fpp = new FilePathProvider(TAG);
		//setup camera
		FrameLayout frameLayout = (FrameLayout) findViewById(R.id.frameLayout);
		camera = getCamera();
		//preview = new CameraPreview(this, camera);
		//frameLayout.addView(preview);
		surfaceView = (SurfaceView) findViewById(R.id.surfaceView1);
		
		EditText et = (EditText) findViewById(R.id.hostText);
		et.setText("192.168.2.2");
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.client, menu);
        return true;
    }
    
	public Camera getCamera(){
		if(camera == null){
			try {
				camera = Camera.open(0);
			} catch (Exception ex) {
				Log.e(TAG, "Problem accessing camera: " + ex.getMessage());
			}
		}
		return camera;
	}
    
    public void connClicked(View view){
		if (!receiving) {
			EditText hostText = (EditText) findViewById(R.id.hostText);
			host = hostText.getText().toString();
			receiveData();
		}
    }
    
    public void receiveData(){
    	Thread receiverThread = new Thread(){
    		public void run() {
    			try{
    				Socket socket = new Socket(host,ServerActivity.PORT);
    				InputStream is = socket.getInputStream();
    				receiving = true;
    				OutputStream videoOs = null;
    				MediaPlayer mp = null;
    				while(socket.isConnected()){
    					if(videoFile == null ){
    						try{
    							videoFile = fpp.getOutputMediaFile(FilePathProvider.MEDIA_TYPE_VIDEO);
    							videoOs = new FileOutputStream(videoFile);
    						}catch(Exception ex){
    							Log.e(TAG, "error creating or opening file", ex);
    						}
    					}
    					if(videoOs != null){
    						byte[] data = new byte[ServerActivity.DATA_BUFFER_SIZE];
    						int bytesRead = 0;
    						BufferedInputStream bis = new BufferedInputStream(is, ServerActivity.DATA_BUFFER_SIZE);
    						int offset = 0;
    						while((bytesRead = bis.read(data, offset, ServerActivity.DATA_BUFFER_SIZE-offset)) > 0 ){
    							currFileLength += bytesRead;
    							bytesRead += offset;
								if (bytesRead > 0 ) {
									offset = 0;
									videoOs.write(data, 0, bytesRead);
									videoOs.flush();
									if (mp == null
											&& currFileLength >= REQ_VIDEO_SIZE) {
										mp = new MediaPlayer();
										playBack(mp, videoFile);
									}
								}else{
									//offset = bytesRead;
								}
    						}
    					}
    					
    				}
    				
    				
    			}catch(Exception ex){
    				Log.e(TAG, "error connecting to :"+host+":"+ServerActivity.PORT);
    				ex.printStackTrace();
    			}finally{
    				receiving = false;
    			}
    			
    		}
    	};
    	receiverThread.start();
    }
    
	public void playBack(MediaPlayer mp, final File file) {
		try {
			//Thread.sleep(1000);
			lastFileLength = currFileLength;
			mp.setDataSource(file.getAbsolutePath());
			//mp.setDisplay(preview.getHolder());
			mp.setDisplay(surfaceView.getHolder());

			mp.setOnCompletionListener(this);
			mp.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {

				@Override
				public void onSeekComplete(MediaPlayer mp) {
					// TODO Auto-generated method stub
					Log.d(TAG, "MediaPlayer onSeekComplete");
					if(!mp.isPlaying()){
						Log.d(TAG, "starting to play again!!!!!");
						
						//mp.start();
					}

				}
			});
			mp.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {

				@Override
				public void onBufferingUpdate(MediaPlayer mp, int percent) {
					// TODO Auto-generated method stub
					Log.d(TAG, "MediaPlayer onBufferingUpdate:" + percent);
				}
			});
			mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {

				@Override
				public boolean onError(MediaPlayer mp, int what, int extra) {
					Log.d(TAG, "MediaPlayer onError what:" + what + " extra:"
							+ extra);
					return true;
				}
			});
			mp.setOnInfoListener(new MediaPlayer.OnInfoListener() {

				@Override
				public boolean onInfo(MediaPlayer mp, int what, int extra) {
					Log.d(TAG, "MediaPlayer onInfo what:" + what + " extra:"
							+ extra);
					return false;
				}
			});
			//mp.setLooping(false);
			mp.setOnPreparedListener(this);
			mp.prepareAsync();
			
			

		} catch (Exception ex) {
			Log.e(TAG, "error preparing mediaplayer", ex);
			ex.printStackTrace();

		}

	}
	
	@Override
	public void onPrepared(MediaPlayer mp) {
		mp.setLooping(false);
		mp.seekTo(lastDuration);
		mp.start();
	}


	@Override
	public void onCompletion(MediaPlayer mp) {
		int totalDelay = 0;
		int currDuration = mp.getDuration();
		mp.reset();
		while(currFileLength - lastFileLength < REQ_VIDEO_SIZE){
			try{
				Thread.sleep(delayTime);
				totalDelay += delayTime;
			}catch(InterruptedException ex){
				ex.printStackTrace();
			}
		}
		lastFileLength = currFileLength;
		try{
			mp.setDataSource(videoFile.getAbsolutePath());
			mp.setDisplay(surfaceView.getHolder());
			lastDuration = currDuration + totalDelay;
			mp.setOnPreparedListener(ClientActivity.this);
			mp.prepareAsync();
		}catch(IOException ex){
			ex.printStackTrace();
		}
			
			
				
	}
    
}
