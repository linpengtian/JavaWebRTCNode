package com.codewithkael.javawebrtcyoutube.repository;

import android.content.Context;
import android.util.Log;

import com.codewithkael.javawebrtcyoutube.remote.SocketClient;
import com.codewithkael.javawebrtcyoutube.utils.DataModel;
import com.codewithkael.javawebrtcyoutube.utils.DataModelType;
import com.codewithkael.javawebrtcyoutube.utils.ErrorCallBack;
import com.codewithkael.javawebrtcyoutube.utils.NewEventCallBack;
import com.codewithkael.javawebrtcyoutube.utils.SuccessCallBack;
import com.codewithkael.javawebrtcyoutube.webrtc.MyPeerConnectionObserver;
import com.codewithkael.javawebrtcyoutube.webrtc.WebRTCClient;
import com.google.gson.Gson;

import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;

public class MainRepository implements WebRTCClient.Listener {

    public Listener listener;
    private final Gson gson = new Gson();
    private final SocketClient socketClient;

    private WebRTCClient webRTCClient;

    private String currentUsername;

    private SurfaceViewRenderer remoteView;

    private String target;
    private void updateCurrentUsername(String username){
        this.currentUsername = username;
    }

    private MainRepository(){
        this.socketClient = new SocketClient();
    }

    private static MainRepository instance;
    public static MainRepository getInstance(){
        if (instance == null){
            instance = new MainRepository();
        }
        return instance;
    }

    public void login(String username, Context context, SuccessCallBack callBack){
        socketClient.login(username,()->{
            updateCurrentUsername(username);
            this.webRTCClient = new WebRTCClient(context,new MyPeerConnectionObserver(){
                @Override
                public void onAddStream(MediaStream mediaStream) {
                    super.onAddStream(mediaStream);
                    try{
                        mediaStream.videoTracks.get(0).addSink(remoteView);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
                    Log.d("TAG", "onConnectionChange: "+newState);
                    super.onConnectionChange(newState);
                    if (newState == PeerConnection.PeerConnectionState.CONNECTED && listener!=null){
                        listener.webrtcConnected();
                    }

                    if (newState == PeerConnection.PeerConnectionState.CLOSED ||
                            newState == PeerConnection.PeerConnectionState.DISCONNECTED ){
                        if (listener!=null){
                            listener.webrtcClosed();
                        }
                    }
                }

                @Override
                public void onIceCandidate(IceCandidate iceCandidate) {
                    super.onIceCandidate(iceCandidate);
                    webRTCClient.sendIceCandidate(iceCandidate,target);
                }
            },username);
            webRTCClient.listener = this;
            callBack.onSuccess();
        });
    }

    public void initLocalView(SurfaceViewRenderer view){
        webRTCClient.initLocalSurfaceView(view);
    }

    public void initRemoteView(SurfaceViewRenderer view){
        webRTCClient.initRemoteSurfaceView(view);
        this.remoteView = view;
    }

    public void startCall(String target){
        webRTCClient.call(target);
    }

    public void switchCamera() {
        webRTCClient.switchCamera();
    }

    public void toggleAudio(Boolean shouldBeMuted){
        webRTCClient.toggleAudio(shouldBeMuted);
    }
    public void toggleVideo(Boolean shouldBeMuted){
        webRTCClient.toggleVideo(shouldBeMuted);
    }
    public void sendCallRequest(String target, ErrorCallBack errorCallBack){
        socketClient.sendMessageToOtherUser(
                new DataModel(target,currentUsername,null, DataModelType.start_call),errorCallBack
        );
    }

    public void endCall(){
        webRTCClient.closeConnection();
    }

    public void subscribeForLatestEvent(NewEventCallBack callBack){
        socketClient.observeIncomingLatestEvent(model -> {
            switch (model.getType()){

                case offer_received:
                    this.target = model.getSender();
                    webRTCClient.onRemoteSessionReceived(new SessionDescription(
                            SessionDescription.Type.OFFER, model.getData()
                    ));
                    webRTCClient.answer(model.getSender());
                    break;
                case answer_received:
                    this.target = model.getSender();
                    webRTCClient.onRemoteSessionReceived(new SessionDescription(
                            SessionDescription.Type.ANSWER,model.getData()
                    ));
                    break;
                case ice_candidate:
                        try{
                            IceCandidate candidate = gson.fromJson(model.getData(),IceCandidate.class);
                            webRTCClient.addIceCandidate(candidate);
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    break;
                case call_response:
                    String message = model.getData();
                    if (message.equals("user is ready for call")) {
                        this.target = model.getSender();
                        callBack.onNewEventReceived(model);
                    } else if (message.equals("user is not online")) {
                        Log.d("MainRepository", "user is not found!");
                    }

                    break;
            }

        });
    }

    @Override
    public void onTransferDataToOtherPeer(DataModel model) {
        socketClient.sendMessageToOtherUser(model,()->{});
    }

    public interface Listener{
        void webrtcConnected();
        void webrtcClosed();
    }
}
