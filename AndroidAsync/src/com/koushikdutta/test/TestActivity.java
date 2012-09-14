package com.koushikdutta.test;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Enumeration;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.os.StrictMode.ThreadPolicy.Builder;
import android.util.Log;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.PushParser;
import com.koushikdutta.async.TapCallback;
import com.koushikdutta.async.callback.ClosedCallback;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.ListenCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpClient.StringCallback;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.HttpConnectCallback;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.koushikdutta.async.http.server.WebSocket;
import com.koushikdutta.async.http.server.WebSocketCallback;

@SuppressLint("NewApi")
public class TestActivity extends Activity {
    ByteBuffer addBytes(ByteBuffer o, byte[] bytes) {
        if (o.remaining() < bytes.length) {
            ByteBuffer n = ByteBuffer.allocate(o.capacity() * 2 + bytes.length);
            n.mark();
            o.limit(o.position());
            o.reset();
            n.put(o);
            o = n;
        }
        o.put(bytes);
        return o;
    }
    
    static final String TAG = "TEST";
    public String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                if (intf.getDisplayName().contains("p2p"))
                    continue;
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e(TAG, ex.toString());
        }
        return null;
    }
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        AsyncHttpServer server = new AsyncHttpServer(3000);
        server.get("/", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
//                response.send("hellO!");
                response.responseCode(200);
                response.write(ByteBuffer.wrap("oh sweet!".getBytes()));
                response.write(ByteBuffer.wrap("oh sweet!".getBytes()));
                response.end();
            }
        });
        
        server.directory(this, "/bootstrap/.*?", "bootstrap/");
        server.directory(this, "/site/.*?", "site/");
        
        server.websocket("/test", new WebSocketCallback() {
            Process process;
            @Override
            public void onConnected(final WebSocket webSocket) {
                new Thread() {
                    public void run() {
                        try {
                            process = Runtime.getRuntime().exec("su -c /system/bin/logcat");
                            new Thread() {
                                public void run() {
                                    try {
                                        DataInputStream dd = new DataInputStream(process.getErrorStream());
                                        String s;
                                        while (null != (s = dd.readLine())) {
                                            if (s.length() == 0)
                                                continue;
                                            webSocket.send(s);
                                        }
                                    }
                                    catch (Exception ex) {
                                        
                                    }
                                };
                                
                            }.start();
                            DataInputStream dis = new DataInputStream(process.getInputStream());
                            OutputStream os = process.getOutputStream();
                            String s;
                            while (null != (s = dis.readLine())) {
                                if (s.length() == 0)
                                    continue;
                                webSocket.send(s);
                                os.write("\n".getBytes());
                            }
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                        Log.i(TAG, "Done!!!!");
                    };
                }.start();
                webSocket.setStringCallback(new WebSocket.StringCallback() {
                    @Override
                    public void onStringAvailable(String s) {
                        Log.i(TAG, s);
                    }
                });
                webSocket.setClosedCallback(new ClosedCallback() {
                    @Override
                    public void onClosed() {
                        try {
                            process.destroy();
                        }
                        catch (Exception e)  {
                            // destroy will cause some angry noise in logcat and throw, since
                            // we are trying to destroy a root process.
                            // the kill succeeds, but it still gets angry.
                            // i assume it succeeds because the various streams are closed.
                        }
                    }
                });
            }
        });
        
        if (true)
            return;
        
//        try {
//            ByteArrayOutputStream bout = new ByteArrayOutputStream();
//            DataOutputStream os = new DataOutputStream(new GZIPOutputStream(bout));
//            os.write("hello asidj aiosjd oiasjd oiasj doas doasj doijq35r90u83qasoidj oaisjdoi asdiohas ihds ".getBytes());
//            os.flush();
//            os.close();
//            byte[] bytes = bout.toByteArray();
//            
//            GZIPTransformer t = new GZIPTransformer() {
//                @Override
//                public void onException(Exception error) {
//                    error.printStackTrace();
//                }
//            };
//            
//            t.setDataCallback(new DataCallback() {
//                @Override
//                public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
//                    bb.spewString();
//                    bb.clear();
//                }
//            });
//            
//            ByteBuffer b = ByteBuffer.wrap(bytes);
//            ByteBufferList bb = new ByteBufferList();
//            bb.add(b);
//            DataEmitter dummy = new DataEmitter() {
//                DataCallback cb;
//                @Override
//                public void setDataCallback(DataCallback callback) {
//                    cb = callback;
//                }
//                
//                @Override
//                public boolean isChunked() {
//                    return false;
//                }
//                
//                @Override
//                public DataCallback getDataCallback() {
//                    return cb;
//                }
//            };
//            dummy.setDataCallback(t);
//            Util.emitAllData(dummy, bb);
//            System.out.println("done");
//        }
//        catch (Exception ex) {
//            ex.printStackTrace();
//        }
//        
//        if (true)
//            return;
        
        

//        final AsyncServer server = new AsyncServer();
        try {
//            server.initialize();
//
//            new Thread() {
//                public void run() {
//                    server.run();
//                };
//            }.start();
            

            /*
            // sending datagram to localhost that is unreceived causes the nio selector to spin...
            // I assume because the kernel does some hackery with piping localhost traffic.
            // use an explicit ip address of a network interface.
            final AsyncSocket dg = server.connectDatagram(new InetSocketAddress(getLocalIpAddress(), 50000));
            dg.setDataCallback(new DataCallback() {
                @Override
                public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                    dg.write(bb);
                    System.out.println("pong");
                }
            });

            new Thread() {
                public void run() {
                    try {
                        final DatagramSocket s = new DatagramSocket(50000);
//                        BufferedDataSink sink = new BufferedDataSink(dg);
                        dg.write(ByteBuffer.wrap(new byte[1000]));

                        DatagramPacket p = new DatagramPacket(new byte[2000], 2000);
                        while (true) {
                            s.receive(p);
                            System.out.println("ping");
                            Thread.sleep(5000);
                            s.send(p);
                            // s.send(p);
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }.start();

            if (true)
                return;
            */

            new Thread() {
                public void run() {
                    try {
                        for (int i = 0; i < 5; i++) {
                            AsyncHttpClient.download("http://builder.clockworkmod.com", new StringCallback() {
                                @Override
                                public void onCompleted(Exception e, AsyncHttpResponse response, String result) {
                                    if (e != null) {
                                        e.printStackTrace();
                                    }
                                    System.out.println(result);
                                }
                            });
                            Thread.sleep(5000);
                        }
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                    }
                };
            }.start();
            
            if (true)
                return;

            for (int i = 0; i < 4; i++) {
//                AsyncHttpGet get = new AsyncHttpGet("https://soighoisdfoihsiohsdf.com");
//                AsyncHttpGet get = new AsyncHttpGet("http://www.cnn.com");
                AsyncHttpGet get = new AsyncHttpGet("https://builder.clockworkmod.com");
                AsyncHttpClient.connect(get, new HttpConnectCallback() {
                    @Override
                    public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                        if (ex != null) {
                            ex.printStackTrace();
                            return;
                        }
                        response.setDataCallback(new DataCallback() {
                            @Override
                            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                                System.out.println(bb.remaining());
                                bb.clear();
                            }
                        });
                        response.setCompletedCallback(new CompletedCallback() {
                            @Override
                            public void onCompleted(Exception ex) {
                                System.out.println("done!");
                                if (ex != null) {
                                    System.out.println("Errors:");
                                    ex.printStackTrace();
                                }
                            }
                        });
                    }
                });
            }

            if (true)
                return;

            AsyncServer.getDefault().listen(InetAddress.getLocalHost(), 50001, new ListenCallback() {
                @Override
                public void onAccepted(final AsyncSocket handler) {
                    final DataCallback dh = new DataCallback() {
                        @Override
                        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                            bb.clear();
                            System.out.println(bb.remaining());
                        }
                    };

                    final PushParser pp = new PushParser(handler);
                    pp
                    .readBuffer(100000)
                    .tap(new TapCallback() {
                       public void tap(byte[] buffer) {
                           System.out.println(buffer.length);

                           pp
                           .readBuffer(15000)
                           .tap(new TapCallback() {
                               public void tap(byte[] buffer) {
                                   System.out.println(buffer.length);

                                   handler.setDataCallback(dh);
                               }
                           });
                           
                       }
                    });
                }

                @Override
                public void onException(Exception error) {
                    error.printStackTrace();
                }
            });

            AsyncServer.getDefault().connectSocket(new InetSocketAddress(InetAddress.getLocalHost(), 50001), new ConnectCallback() {
                @Override
                public void onConnectCompleted(Exception ex, final AsyncSocket socket) {
                    if (ex != null) {
                        System.out.println("connect fail");
                        return;
                    }
                
                    new Thread() {
                        @Override
                        public void run() {
                            try {
                                BufferedDataSink ds = new BufferedDataSink(socket);
                                ByteBuffer b = ByteBuffer.allocate(30000);
                                ds.write(b);
                                Thread.sleep(1000);
                                b = ByteBuffer.allocate(30000);
                                ds.write(b);
                                Thread.sleep(1000);
                                b = ByteBuffer.allocate(30000);
                                ds.write(b);
                                Thread.sleep(1000);
                                b = ByteBuffer.allocate(30000);
                                ds.write(b);
                            }
                            catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }.start();
                }
            });

            
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}