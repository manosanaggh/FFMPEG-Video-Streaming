package ffmpeg_streaming_video;

import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JButton;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ClassNotFoundException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;

public class StreamingServer implements Runnable
{
	private static ServerSocket server;
	
	private Thread serverThread;
	
	private JFrame frame;
	private JLabel labelLog;
	private JTextArea textLog;
	private JButton btnStart;
	private JButton btnStop;
	
	static Logger log = LogManager.getLogger(StreamingServer.class);

	/* Function that generates all the needed videos for the client to choose from. */
	void generate_videos() throws NullPointerException, IOException
	{
		textLog.append("Creating the missing videos for all available formats with lower resolutions...\n");

		String outputDirStr = "videos/";
		
		FFmpeg ffmpeg = null;
		FFprobe ffprobe = null;

		try
		{
			log.debug("Initialising FFMpegClient");
			ffmpeg = new FFmpeg("/usr/bin/ffmpeg");
			ffprobe = new FFprobe("/usr/bin/ffprobe");
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		// Array of videos from the /videos directory.
		File[] existingVideos = new File(outputDirStr).listFiles();
		
		// Array of video formats to generate.
		String[] videoFormats = {".avi", ".mp4", ".mkv"};

		// List of supported resolutions.
		ArrayList<Integer> resolutions = new ArrayList<>();
		resolutions.add(240);
		resolutions.add(360);
		resolutions.add(480);
		resolutions.add(720);
		resolutions.add(1080);

		ArrayList<String> checkedNames = new ArrayList<>();

		// For each existing video...
		for(File video : existingVideos)
		{
			log.debug("Video found: " + video.getName());
			String currentVideoName = video.getName();
			String[] parts = currentVideoName.split("[-.]"); 

			String name = parts[0];
			if(checkedNames.contains(name))
				continue;
			else 
				checkedNames.add(name);
			
			int resolution = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
			String extension = parts[2];

			// and for each video format...
			for(String format : videoFormats)
			{
				boolean currentFormat = false;
				if(format.split("[.]")[1].equals(extension))
					currentFormat = true;
					
				// and for each resolution...
				for (Integer res : resolutions) 
				{
					if(currentFormat && res == resolution || res > resolution)
						continue;

					log.debug("Converting '" + currentVideoName + "' to '" + format  + "' with " + res + "p resolution");
					
					// generate the video file 
					// with the appropriate resolution tag at the title
					// and the appropriate video format extension.
					FFmpegBuilder builder = null;
					int width, height;
					log.debug("Creating the transcoding");
					switch (res) {
						case 1080: {
							width = 1920;
							height = 1080;
							builder = (new FFmpegBuilder()
							.setInput(outputDirStr + video.getName())
							.addOutput(outputDirStr + name + "-" + res + "p" + format)
							.setVideoResolution(width, height)
							.done());
							break;
						}
						case 720: {
							width = 1280;
							height = 720;
							builder = (new FFmpegBuilder()
							.setInput(outputDirStr + video.getName())
							.addOutput(outputDirStr + name + "-" + res + "p" + format)
							.setVideoResolution(width, height)
							.done());
							break;
						}
						case 480: {
							width = 854;
							height = 480;
							builder = (new FFmpegBuilder()
							.setInput(outputDirStr + video.getName())
							.addOutput(outputDirStr + name + "-" + res + "p" + format)
							.setVideoResolution(width, height)
							.done());
							break;
						}
						case 360: {
							width = 640;
							height = 360;
							builder = (new FFmpegBuilder()
							.setInput(outputDirStr + video.getName())
							.addOutput(outputDirStr + name + "-" + res + "p" + format)
							.setVideoResolution(width, height)
							.done());
							break;
						}
						case 240: {
							width = 426;
							height = 240;
							builder = (new FFmpegBuilder()
							.setInput(outputDirStr + video.getName())
							.addOutput(outputDirStr + name + "-" + res + "p" + format)
							.setVideoResolution(width, height)
							.done());
							break;
						}
						default: {
							log.error("Format not supported!");
							break;
						}
					}
					
					log.debug("Creating the executor");
					FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
					
					log.debug("Starting the transcoding");
					
					executor.createJob(builder).run();
					
					log.debug("Transcoding finished");	
				}
			}
		}	
		
		log.debug("Done!");
		textLog.append("Videos created successfuly!\n");
	}

	/* Function to start the server. */
	void start_server() throws IOException, ClassNotFoundException
	{
		try
		{
			generate_videos();
		}
		catch (IOException ioException)
		{
			ioException.printStackTrace();
		}

		server = new ServerSocket(5000);
		
		// List with the filenames in the /videos/ directory.
		File[] videosList = new File("videos/").listFiles();	
		
		while(true)
		{
			textLog.append("Listening for requests...\n");
			
			Socket socket = server.accept();
			ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
			ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
			
			// Receive the request (download speed and format) from client.
			ArrayList<String> receivedRequest = (ArrayList<String>) inputStream.readObject();

			String networkSpeedStr = receivedRequest.get(0);
			double networkSpeedMbps = Double.parseDouble(networkSpeedStr);
			double networkSpeedKbps = networkSpeedMbps*1000;
			String selectedFormat = receivedRequest.get(1);
			
			textLog.append("Received request for " + selectedFormat + " format.\n");
			
			// List of videos available to stream.
			ArrayList<String> availableVideos = new ArrayList<>();
			
			// For each video in the /videos/ directory...
			for(File video : videosList)
			{
				String currentVideo = video.getName();
				
				// take the last 3 characters of the filenames (e.g. .avi, .mp4, .mkv) 
				// and compare them to the received format to filter out the rest
				// of the videos at another format.
				if(currentVideo.substring(currentVideo.length()-3).equals(selectedFormat)){
					switch(currentVideo.split("[-.]")[1]){
						case "240p":{
							if (networkSpeedKbps < 400)
								continue;
							availableVideos.add(currentVideo);
							break;
						}
						case "360p":{
							if (networkSpeedKbps < 750)
								continue;
							availableVideos.add(currentVideo);
							break;
						}
						case "480p":{
							if (networkSpeedKbps < 1000)
								continue;
							availableVideos.add(currentVideo);
							break;
						}
						case "720p":{
							if (networkSpeedKbps < 2500)
								continue;
							availableVideos.add(currentVideo);
							break;
						}
						case "1080p":{
							if (networkSpeedKbps < 4500)
								continue;
							availableVideos.add(currentVideo);
							break;
						}
						default: break;
					}
				}
			}
			
			// Send the list of videos that are available to stream based on
			// the specified format and resolution.
			outputStream.writeObject(availableVideos);
			
			// Receive the selected video and the protocol specification to stream with.
			ArrayList<String> stream_specs = (ArrayList<String>) inputStream.readObject();
			String selectedVideo = stream_specs.get(0);
			String selectedProtocol = stream_specs.get(1);
			
			textLog.append("Using the " + selectedProtocol + " protocol to stream '" + selectedVideo + "'.\n\n");
			
			String videosDirFullpath = System.getProperty("user.dir") + "/videos";
			
			// Create a process through the command line to run the ffplay program
			// to play the incoming streamed video with the appropriate arguments.
			ArrayList<String> commandLineArgs = new ArrayList<>();

			commandLineArgs.add("ffmpeg");
		
			if(selectedProtocol.equals("UDP"))
			{
				commandLineArgs.add("-re");
				commandLineArgs.add("-i");
				commandLineArgs.add(videosDirFullpath + "/" + selectedVideo);
				commandLineArgs.add("-f");
				commandLineArgs.add("mpegts");
				commandLineArgs.add("udp://127.0.0.1:6000");
			}
			else if(selectedProtocol.equals("TCP"))
			{
				commandLineArgs.add("-re");
				commandLineArgs.add("-i");
				commandLineArgs.add(videosDirFullpath + "/" + selectedVideo);
				commandLineArgs.add("-f");
				commandLineArgs.add("mpegts");
				commandLineArgs.add("tcp://127.0.0.1:5100");
			}
			else if(selectedProtocol.equals("RTP/UDP"))	
			{
				commandLineArgs.add("-re");
				commandLineArgs.add("-i");
				commandLineArgs.add(videosDirFullpath + "/" + selectedVideo);
				commandLineArgs.add("-an");
				commandLineArgs.add("-c:v");
				commandLineArgs.add("libx264");
				commandLineArgs.add("-f");
				commandLineArgs.add("rtp");
				commandLineArgs.add("rtp://127.0.0.1:5004");
				commandLineArgs.add("-sdp_file");
				commandLineArgs.add(System.getProperty("user.dir") + "/video.sdp");
			}
			else
				switch(selectedVideo.split("[-.]")[1]){
					case "240p":{
						commandLineArgs.add("-re");
						commandLineArgs.add("-i");
						commandLineArgs.add(videosDirFullpath + "/" + selectedVideo);
						commandLineArgs.add("-f");
						commandLineArgs.add("mpegts");
						commandLineArgs.add("tcp://127.0.0.1:5100");
						break;
					}
					case "360p":{
						commandLineArgs.add("-re");
						commandLineArgs.add("-i");
						commandLineArgs.add(videosDirFullpath + "/" + selectedVideo);
						commandLineArgs.add("-f");
						commandLineArgs.add("mpegts");
						commandLineArgs.add("udp://127.0.0.1:6000");
						break;
					}
					case "480p":{
						commandLineArgs.add("-re");
						commandLineArgs.add("-i");
						commandLineArgs.add(videosDirFullpath + "/" + selectedVideo);
						commandLineArgs.add("-f");
						commandLineArgs.add("mpegts");
						commandLineArgs.add("udp://127.0.0.1:6000");
						break;
					}
					case "720p":{
						commandLineArgs.add("-re");
						commandLineArgs.add("-i");
						commandLineArgs.add(videosDirFullpath + "/" + selectedVideo);
						commandLineArgs.add("-an");
						commandLineArgs.add("-c:v");
						commandLineArgs.add("libx264");
						commandLineArgs.add("-f");
						commandLineArgs.add("rtp");
						commandLineArgs.add("rtp://127.0.0.1:5004");
						commandLineArgs.add("-sdp_file");
						commandLineArgs.add(System.getProperty("user.dir") + "/video.sdp");
						break;
					}
					case "1080p":{
						commandLineArgs.add("-re");
						commandLineArgs.add("-i");
						commandLineArgs.add(videosDirFullpath + "/" + selectedVideo);
						commandLineArgs.add("-an");
						commandLineArgs.add("-c:v");
						commandLineArgs.add("libx264");
						commandLineArgs.add("-f");
						commandLineArgs.add("rtp");
						commandLineArgs.add("rtp://127.0.0.1:5004");
						commandLineArgs.add("-sdp_file");
						commandLineArgs.add(System.getProperty("user.dir") + "/video.sdp");
						break;
					}
					default: break;
			}

			try{
				Thread.sleep(500);
			}catch (InterruptedException e){
				e.printStackTrace();
			}

			ProcessBuilder process_builder = new ProcessBuilder(commandLineArgs);
			process_builder.start();
			outputStream.close();
			inputStream.close();
			socket.close();
		}
	}
	
	@Override
	public void run() 
	{
		try 
		{
			start_server();
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
		}
	}
	
	public StreamingServer() 
	{
		frame = new JFrame("Streaming Server");
		frame.setBounds(100, 100, 450, 300);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(null);
		frame.setResizable(false);
		
		// Server Log Text with its scrollbar.
		labelLog = new JLabel("Logs:");
		labelLog.setBounds(20, 11, 66, 14);
		frame.getContentPane().add(labelLog);
		
		textLog = new JTextArea();
		textLog.setBounds(10, 36, 424, 181);
		textLog.setWrapStyleWord(true);
		textLog.setLineWrap(true);
		frame.getContentPane().add(textLog);
		textLog.setColumns(10);
		
		JScrollPane scrollPane = new JScrollPane(textLog);
		scrollPane.setBounds(20, 36, 404, 181);
		frame.getContentPane().add(scrollPane);
		//--------------------------------------
		
		// Start Server Button.
		btnStart = new JButton("Start");
		btnStart.setBounds(20, 228, 113, 23);
		frame.getContentPane().add(btnStart);
		//--------------------------------------
		
		// Stop Server Button.
		btnStop = new JButton("Stop");
		btnStop.setBounds(311, 228, 113, 23);
		frame.getContentPane().add(btnStop);
		//--------------------------------------
		
		// Hide the stop server button on startup.
		btnStop.setEnabled(false); 

		// Create a thread for the server to run.
		serverThread = new Thread(this);
		
		// Implementation of the listener after the Start Server button is pressed.
		btnStart.addActionListener(event -> {
			log.debug("'Start Server' button has been pressed");

			btnStart.setEnabled(false);
			btnStop.setEnabled(true);

			serverThread.start();
		});
		
		// Implementation of the listener after the Stop Server button is pressed.
		btnStop.addActionListener(event -> {
			log.debug("'Stop Server' button has been pressed");

			// Close the GUI window of the server.
			System.exit(0);	
		});
	}
	
	public static void main(String[] args) 
	{
		EventQueue.invokeLater(() -> {
			try
			{
				StreamingServer window = new StreamingServer();
				window.frame.setVisible(true);
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		});
	}
}
