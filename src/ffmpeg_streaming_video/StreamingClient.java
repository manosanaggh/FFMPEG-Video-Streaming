package ffmpeg_streaming_video;

import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JButton;
import javax.swing.JComboBox;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.util.ArrayList;

import java.net.URL;

public class StreamingClient
{
	private Socket socket;
	private ObjectOutputStream outputStream;
	private ObjectInputStream inputStream;
	
	private static JFrame frame;
	
	private JLabel labelFormat;
	private JComboBox format;
	private JButton btnStart;

	private JLabel labelVideo;
	private JComboBox video;
	private JLabel labelProtocol;
	private JComboBox protocol;
	private JButton btnStream;
	
	static Logger log = LogManager.getLogger(StreamingClient.class);

	/* Function that calculates the network speed of the client. */
    double getNetworkSpeed() {
        String fileUrl = "http://speedtest.tele2.net/1MB.zip"; // Test file.
        int testDurationSeconds = 5;
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000); // 5 seconds timeout.
            connection.connect();

            if (connection.getResponseCode() / 100 != 2) {
                log.error("Failed to connect: " + connection.getResponseCode());
                return -1;
            }

            InputStream inputStream = connection.getInputStream();
            byte[] buffer = new byte[4096];
            long totalBytesRead = 0;
            long startTime = System.currentTimeMillis();

            while (true) {
                int bytesRead = inputStream.read(buffer);
                if (bytesRead == -1) break;
                totalBytesRead += bytesRead;

                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime >= testDurationSeconds * 1000) {
                    break;
                }
            }

            long endTime = System.currentTimeMillis();
            double timeSeconds = (endTime - startTime) / 1000.0;

            double bitsDownloaded = totalBytesRead * 8.0;
            double megabitsPerSecond = bitsDownloaded / (timeSeconds * 1_000_000.0);

            log.debug("Download speed: " + String.format("%.1f",megabitsPerSecond)  + " Mbps.");

            inputStream.close();
            connection.disconnect();
			return megabitsPerSecond;

        } catch (Exception e) {
            e.printStackTrace();
        }
		return -1;
    }	

	/* Function used to send requests to the server. */
	void sendRequestToServer(ObjectOutputStream outputStream, ObjectInputStream inputStream) throws Exception{
		double networkSpeed = getNetworkSpeed();

		if(networkSpeed == -1)
			log.error("Network speed calculation failed!");
		
		ArrayList<String> request = new ArrayList<>();
		String networkSpeedStr = Double.toString(networkSpeed);
		request.add(Double.toString(networkSpeed));
		request.add(format.getSelectedItem().toString());
		
		log.debug("Sending request to server: " + String.format("%.1f",networkSpeed) + " Mbps download speed and " + format.getSelectedItem().toString() + " format");
		
		// Send the request with the network speed and the selected format.
		outputStream.writeObject(request);	
		
		ArrayList<String> available_videos = (ArrayList<String>) inputStream.readObject(); // receive a list of videos based on the request
		log.debug("Received list of available videos to stream");
		
		// Fill the dropdown menu of the videos on the gui with the contents of this list.
		for(String currentVideo : available_videos)
			video.addItem(currentVideo);	
		
		log.debug("Sent the list to the GUI");
	}
	
	/* Function used to send the stream specifications to the server. */
	void sendSpecsToServer(ObjectOutputStream outputStream) throws Exception
	{
		ArrayList<String> streamSpecs = new ArrayList<>();
		streamSpecs.add(video.getSelectedItem().toString());
		streamSpecs.add(protocol.getSelectedItem().toString());
	
		log.debug("Sending stream specs to server: " + video.getSelectedItem().toString() + " using " + protocol.getSelectedItem().toString());
		outputStream.writeObject(streamSpecs);
		
		// Create a process through the command line to run the ffplay program
		// to play the incoming streamed video with the appropriate arguments.
		ArrayList<String> commandLineArgs = new ArrayList<>();

		commandLineArgs.add("ffplay");
		
		if(protocol.getSelectedItem().toString().equals("UDP"))
			commandLineArgs.add("udp://127.0.0.1:6000");
		else if(protocol.getSelectedItem().toString().equals("TCP"))
			commandLineArgs.add("tcp://127.0.0.1:5100?listen");
		else if(protocol.getSelectedItem().toString().equals("RTP/UDP"))	 
		{
			// For RTP/UDP, mention the session description protocol file to the server.
			commandLineArgs.add("-protocol_whitelist");
			commandLineArgs.add("file,rtp,udp");
			commandLineArgs.add("-i");
			commandLineArgs.add("video.sdp");
		}
		else
			switch(video.getSelectedItem().toString().split("[-.]")[1]){
				case "240p":{
					commandLineArgs.add("tcp://127.0.0.1:5100?listen");
					break;
				}
				case "360p":{
					commandLineArgs.add("udp://127.0.0.1:6000");
					System.out.println("Play UDP");
					break;
				}
				case "480p":{
					commandLineArgs.add("udp://127.0.0.1:6000");
					break;
				}
				case "720p":{
					commandLineArgs.add("-protocol_whitelist");
					commandLineArgs.add("file,rtp,udp");
					commandLineArgs.add("-i");
					commandLineArgs.add("video.sdp");
					break;
				}
				case "1080p":{
					commandLineArgs.add("-protocol_whitelist");
					commandLineArgs.add("file,rtp,udp");
					commandLineArgs.add("-i");
					commandLineArgs.add("video.sdp");
					break;
				}
				default: break;
		}
		
		ProcessBuilder processBuilder = new ProcessBuilder(commandLineArgs);
		processBuilder.start();
		
		log.debug("Process to play the incoming stream started");
	}
	
	public StreamingClient() throws IOException
	{
		// Setting up the socket address and port
		// and the output and input streams to send and receive data to the server.
		socket = new Socket("127.0.0.1", 5000);
		outputStream = new ObjectOutputStream(socket.getOutputStream());
		inputStream = new ObjectInputStream(socket.getInputStream());
		
		frame = new JFrame("Streaming Client");
		frame.setBounds(250, 250, 250, 280);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(null);
		frame.setResizable(false);	
	
		//--------------------------------------
		
		// Format Dropdown Menu (with its choices).
		labelFormat = new JLabel("Type");
		labelFormat.setBounds(108, 11, 43, 14);
		frame.getContentPane().add(labelFormat);
		
		format = new JComboBox();
		format.setBounds(82, 36, 89, 20);
		frame.getContentPane().add(format);
		format.addItem("avi");
		format.addItem("mp4");
		format.addItem("mkv");
		//--------------------------------------
		
		// Start Button.
		btnStart = new JButton("Start");
		btnStart.setBounds(82, 67, 89, 23);
		frame.getContentPane().add(btnStart);
		//--------------------------------------
	
		// Video Dropdown Menu (with its choices added later, sent by the server).
		labelVideo = new JLabel("Video");
		labelVideo.setBounds(10, 101, 100, 14);
		frame.getContentPane().add(labelVideo);
		
		video = new JComboBox();
		video.setBounds(10, 126, 224, 20);
		frame.getContentPane().add(video);
		//--------------------------------------
		
		// Protocol Dropdown Menu (with its choices).
		labelProtocol = new JLabel("With");
		labelProtocol.setBounds(10, 162, 51, 14);
		frame.getContentPane().add(labelProtocol);
		
		protocol = new JComboBox();
		protocol.setBounds(10, 187, 89, 20);
		frame.getContentPane().add(protocol);
		protocol.addItem("DEFAULT");
		protocol.addItem("UDP");
		protocol.addItem("TCP");
		protocol.addItem("RTP/UDP");
		//--------------------------------------
		
		// Stream Button.
		btnStream = new JButton("Stream");
		btnStream.setBounds(82, 218, 89, 23);
		frame.getContentPane().add(btnStream);	
		//--------------------------------------
		
		// Hide the components that are to be used after the first response of the server.
		video.setEnabled(false);
		protocol.setEnabled(false);
		btnStream.setEnabled(false);
		
		// Implementation of the listener after the connect button is pressed.
		btnStart.addActionListener(event -> {
			log.debug("Connect button has been pressed");

			try
			{
				// Send the request (resolution and format) to the server
				// and receive a list of videos based on the request.
				sendRequestToServer(outputStream, inputStream);

				// Hide the components already used for the first response of the server.
				format.setEnabled(false);
				btnStart.setEnabled(false);

				// Enable the components to be used for the second response of the server.
				video.setEnabled(true);
				protocol.setEnabled(true);
				btnStream.setEnabled(true);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		});

		// Implementation of the listener after the Stream button is pressed.
		btnStream.addActionListener(event -> {
			log.debug("'Stream' button has been pressed");

			try
			{
				// Send the specifications (selected video and protocol) to the server
				// and stream the incoming video through ffplay.
				sendSpecsToServer(outputStream);

				// Close the socket and streams from the client when all
				// communications are done.
				outputStream.close();
				inputStream.close();
				socket.close();

				// Close the GUI window of the client.
				System.exit(0);	
			}
				catch (Exception e)
				{
				e.printStackTrace();
			}
		});
	}
	
	public static void main(String[] args) 
	{
		EventQueue.invokeLater(() -> {
			try
			{
				StreamingClient window = new StreamingClient();
				window.frame.setVisible(true);
			}
			catch(ConnectException e)
			{
				JOptionPane.showMessageDialog(frame, "Connection refused. Start the server and try again.", "Exiting...", JOptionPane.ERROR_MESSAGE);
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}

		});
	}
}

