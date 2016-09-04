package lt.ekgame.autohost;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import lt.ekgame.bancho.api.packets.Packet;
import lt.ekgame.bancho.api.packets.server.PacketRoomEveryoneFinished;
import lt.ekgame.bancho.api.packets.server.PacketRoomJoined;
import lt.ekgame.bancho.api.packets.server.PacketRoomScoreUpdate;
import lt.ekgame.bancho.api.packets.server.PacketRoomUpdate;
import lt.ekgame.bancho.api.units.Beatmap;
import lt.ekgame.bancho.api.units.MultiplayerRoom;
import lt.ekgame.bancho.client.MultiplayerHandler;
import lt.ekgame.bancho.client.PacketHandler;
import lt.ekgame.beatmap_analyzer.calculator.Difficulty;
import lt.ekgame.beatmap_analyzer.calculator.Performance;
import lt.ekgame.beatmap_analyzer.calculator.PerformanceCalculator;
import lt.ekgame.beatmap_analyzer.parser.BeatmapException;
import lt.ekgame.beatmap_analyzer.parser.BeatmapParser;
import lt.ekgame.beatmap_analyzer.utils.Mod;
import lt.ekgame.beatmap_analyzer.utils.Mods;
import lt.ekgame.beatmap_analyzer.utils.ScoreVersion;

public class RoomHandler implements PacketHandler {
	
	public AutoHost bot;
	public double scores[][] = new double[16][5];
	private int slotsTaken;
	public lt.ekgame.beatmap_analyzer.Beatmap currentBeatmap[] = new lt.ekgame.beatmap_analyzer.Beatmap[16];
	public String modList[] = new String[16];
	//public BeatmapParser parser;
	public TimerThread timer;
	private List<Integer> skipVotes = new ArrayList<>();
	public RoomHandler(AutoHost bot) {
		this.bot = bot;
	}
	
	public void registerVoteSkip(int userId, String userName) {
		if (!skipVotes.contains(userId)) {
			skipVotes.add(userId);
			bot.bancho.sendMessage("#multiplayer", userName+" Voted for skipping the song! ("+skipVotes.size()+"/"+slotsTaken/2+")" );
		}
		if (((double)skipVotes.size())/((double)slotsTaken) > 0.5) {
			MultiplayerHandler mp = bot.bancho.getMultiplayerHandler();
			mp.setBeatmap(bot.beatmaps.nextBeatmap());
		    bot.bancho.sendMessage("#multiplayer", "The beatmap was voted off.");
			onBeatmapChange();
		}
	}

	public void resetVoteSkip() {
		skipVotes.clear();
	}
	
	public void onBeatmapChange() {
		resetVoteSkip();
		timer.resetTimer();
		Beatmap beatmap = bot.beatmaps.getBeatmap();
		if (beatmap == null) {
			bot.bancho.sendMessage("#multiplayer", "No more beatmaps in the queue. Use !add [link to beatmap] to add more beatmaps.");
		} else {
			bot.bancho.sendMessage("#multiplayer", String.format("Up next: %s - %s [%s] mapped by %s.", 
					beatmap.getArtist(), beatmap.getTitle(), beatmap.getVersion(), beatmap.getCreator()));
			  try {
					RequestConfig defaultRequestConfig = RequestConfig.custom()
						    .setSocketTimeout(10000)
						    .setConnectTimeout(10000)
						    .setConnectionRequestTimeout(10000)
						    .build();
					
					HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
					URI uri = new URIBuilder()
							.setScheme("http")
							.setHost("osu.ppy.sh")
							.setPath("/osu/"+beatmap.getId())
							.build();
					HttpGet request = new HttpGet(uri);
					HttpResponse response = httpClient.execute(request);
					InputStream content = response.getEntity().getContent();
					//String stringContent = IOUtils.toString(content, "UTF-8");
					BeatmapParser parser = new BeatmapParser();
					lt.ekgame.beatmap_analyzer.Beatmap cbp = parser.parse(content);
					for (int i = 0; i < 16; i++)  {
						currentBeatmap[i] = cbp;
					}
					Performance perf = cbp.getPerformance(cbp.getMaxCombo(),0,0,0);
					long ssNOMOD = ((long) perf.getPerformance());
					lt.ekgame.beatmap_analyzer.Beatmap cbp2 = cbp;
					lt.ekgame.beatmap_analyzer.Beatmap cbp3 = cbp;
			  		lt.ekgame.beatmap_analyzer.Beatmap cbp4 = cbp;

					cbp2 = cbp2.applyMods(new Mods(Mod.HIDDEN));
					Performance perf2 = cbp2.getPerformance(cbp.getMaxCombo(),0,0,0);
					long ssHIDDEN = ((long) perf2.getPerformance());
					cbp3 = cbp3.applyMods(new Mods(Mod.HARDROCK));
					Performance perf3 = cbp3.getPerformance(cbp.getMaxCombo(),0,0,0);
					long ssHR = ((long) perf3.getPerformance());
					cbp4 = cbp4.applyMods(new Mods(Mod.HIDDEN,Mod.HARDROCK));
					Performance perf4 = cbp4.getPerformance(cbp.getMaxCombo(),0,0,0);
					long ssHDHR = ((long) perf4.getPerformance());
					bot.bancho.sendMessage("#multiplayer","NOMOD: "+ssNOMOD+"pp || HD: "+ssHIDDEN+"pp || HR: "+ssHR+"pp || HDHR: "+ssHDHR+"pp");
					
			  }	catch ( JSONException | IOException | URISyntaxException | BeatmapException e) {
					e.printStackTrace();
					bot.bancho.sendMessage("#multiplayer", "Error Parsing beatmap");
		 }
		}
	}


	@Override
	public void handle(Packet packet) {
		MultiplayerHandler mp = bot.bancho.getMultiplayerHandler();
		
		if (packet instanceof PacketRoomJoined) {
			bot.beatmaps.reset();
			mp.setBeatmap(bot.beatmaps.nextBeatmap());
			timer = new TimerThread(this);
			timer.start();
		}
		
		if (packet instanceof PacketRoomScoreUpdate) {
			  PacketRoomScoreUpdate update = (PacketRoomScoreUpdate) packet;
			  Beatmap current = bot.beatmaps.getBeatmap();
			  int userID = mp.getRoom().slotId[update.byte1];
			  int bID = current.getId();

			  try {
				  //lt.ekgame.beatmap_analyzer.Beatmap beatmap = currentBeatmap[update.byte1];
				  String[] modNames = {"NF", "EZ", "", "HD", "HR", "SD", "DT", "RX", "HT", "FL", "AP", "SO", "AO", "PF"};

				  modList[update.byte1] = "";
				  for (int i = 0; i < 14; i++) {
				      // stupid NC
				      if (i == 6 && (mp.getRoom().slotMods[update.byte1] & 576) != 0) modList[update.byte1] += "NC";
				      if ((mp.getRoom().slotMods[update.byte1] & (int)Math.pow(2, i)) != 0) {
				          modList[update.byte1] += modNames[i];
				      }
				  }
				  
				  if (modList[update.byte1].equals("")) 
				  { modList[update.byte1] = "NOMOD"; }
				  //List<Mod> mods = Mod.getMods(mp.getRoom().slotMods[update.byte1]);
				  
			  int MaxComboPossible = currentBeatmap[update.byte1].getMaxCombo();			  
			  int MaxCombo = update.short1+update.short2+update.short3+update.short4+update.short5+update.short6;
			  int MaxComboHit = update.short7;
			  int accok = update.short1+update.short4;
			  int accmeh = update.short2+update.short5;
			  int accf = update.short3;
			  int accmiss = update.short8;
			  Performance perf =  currentBeatmap[update.byte1].getPerformance(MaxComboHit,accmeh,accf,accmiss);
			  
			  //System.out.println(mp.getRoom().slotMods[update.byte1]); // 0-NOMOD NF-1 8-HD 16-HR 24-HDHR 

			  
			  scores[update.byte1][0] = mp.getRoom().slotId[update.byte1];
			  scores[update.byte1][1] = update.integer2;
			  scores[update.byte1][2] = update.byte1;
			  scores[update.byte1][3] = (long) (perf.getAccuracy()*100);
			  scores[update.byte1][4] = (long) perf.getPerformance();
			  
			  Arrays.sort(scores, new Comparator<double[]>() {				  
				  public int compare(double[] o1, double[] o2) { 
					    return Double.compare(o2[1], o1[1]);
					}
				});
			  //System.out.println(""+update.integer1); Time passed in ms
			  //System.out.println("byte1: "+update.byte1); // Lobby Slot
			  //System.out.println("short1: "+update.short1); // Correct x300s
			  //System.out.println("short2: "+update.short2); // x100s
			  //System.out.println("short3: "+update.short3); // x50s
			  //System.out.println("short4: "+update.short4); // Extra x300s
			  //System.out.println("short5: "+update.short5); // Extra x100s 
			  //System.out.println("short6: "+update.short6); // Misses
			  //System.out.println("short7: "+update.short7); // Max Combo
			  //System.out.println("short8: "+update.short8); // Current Combo
			  //System.out.println("integer2: "+update.integer2); // Score
			  //System.out.println("boolean1: "+update.boolean1); // False always..
			  //System.out.println("byte2: "+update.byte2); // 200-100?
			  //System.out.println("byte3: "+update.byte3); // 1-6?
			  //System.out.println("boolean2: "+update.boolean2); // Alive
			  }	catch ( JSONException /*| BeatmapException*/ e) {
					e.printStackTrace();
					bot.bancho.sendMessage("#multiplayer", "Error Parsing beatmap");
		}
		}
		
		if (packet instanceof PacketRoomUpdate && mp.isHost()) {
			PacketRoomUpdate update = (PacketRoomUpdate) packet;
			if (update.room.matchId == mp.getMatchId()) {
				byte[] status = update.room.slotStatus;
				String statuses = "";
				slotsTaken = 0;
				int slotsReady = 0;
				for (int i = 0; i < 16; i++)  {
					statuses += status[i] + " ";
					if (status[i] != 1 && status[i] != 2) {
						if (update.room.slotId[i] != bot.bancho.getClientHandler().getUserId()) {
							slotsTaken++;
							if (status[i] == 8)
								slotsReady++;
						}
					}
				}
				
				//System.out.println(statuses);
				if (slotsTaken > 0 && slotsTaken == slotsReady) {
					startGame();
					timer.skipEvents();
				}
			}
		}
		
		if (packet instanceof PacketRoomEveryoneFinished) {
			resetVoteSkip();
			mp.setBeatmap(bot.beatmaps.nextBeatmap());
			onBeatmapChange();
			String IDs[] = new String[3];
			
		try {
			for (int i = 0; i < 4; i++)  {
				if (scores[i][1] != 0) {
		RequestConfig defaultRequestConfig = RequestConfig.custom()
			    .setSocketTimeout(10000)
			    .setConnectTimeout(10000)
			    .setConnectionRequestTimeout(10000)
			    .build();
		HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
		URI uri = new URIBuilder()
				.setScheme("http")
				.setHost("osu.ppy.sh")
				.setPath("/api/get_user")
				.setParameter("k", "d25a19b770b2b69149d2fd20092475a05026df04")
				.setParameter("u", ""+scores[i][0])
				.setParameter("type", "id")
				.build(); 
		HttpGet request = new HttpGet(uri);
		HttpResponse response = httpClient.execute(request);
		InputStream content = response.getEntity().getContent();
		String stringContent = IOUtils.toString(content, "UTF-8");
		JSONArray array = new JSONArray(stringContent);
		String username = array.getJSONObject(0).getString("username");
		IDs[0] = username;		
			}
			}
			} catch (URISyntaxException | IOException e) {
			e.printStackTrace();
		}
			if (scores[0][1] != 0){
				bot.bancho.sendMessage("#multiplayer", "1st Place "+ IDs[0]+" "+ modList[ (int) scores[0][2] ]+" "+scores[0][3]+"% PP:"+scores[0][4]);				
			}
			if (scores[1][1] != 0){
			bot.bancho.sendMessage("#multiplayer", "2nd Place "+ IDs[1]+" "+ modList[ (int) scores[1][2] ]+" "+scores[0][3]+"% PP:"+scores[1][4]);
			}
			if (scores[2][1] != 0){
			bot.bancho.sendMessage("#multiplayer", "3rd Place "+ IDs[2]+" "+ modList[ (int) scores[2][2] ]+" "+scores[0][3]+"% PP:"+scores[2][4]);
			}
		}
	}
	
	public void startGame() {
		MultiplayerHandler mp = bot.bancho.getMultiplayerHandler();
		mp.setReady(true);
		mp.startGame();
		for (int i = 0; i < scores.length; i++) 
		{
			Arrays.fill(scores[i],0);
			  byte[] status = mp.getRoom().slotStatus;
			  if (status[i] != 1) {
				  if ((mp.getRoom().slotMods[i] & 1) != 0)
				  {  currentBeatmap[i] = currentBeatmap[i].applyMods(new Mods(Mod.NO_FAIL)); }
				  if ((mp.getRoom().slotMods[i] & 2) != 0)
				  {  currentBeatmap[i] = currentBeatmap[i].applyMods(new Mods(Mod.EASY)); }
				  if ((mp.getRoom().slotMods[i] & 8) != 0)
				  {  currentBeatmap[i] = currentBeatmap[i].applyMods(new Mods(Mod.HIDDEN)); }
				  if ((mp.getRoom().slotMods[i] & 16) != 0)
				  {  currentBeatmap[i] = currentBeatmap[i].applyMods(new Mods(Mod.HARDROCK)); }
				  if ((mp.getRoom().slotMods[i] & 32) != 0)
				  {  currentBeatmap[i] = currentBeatmap[i].applyMods(new Mods(Mod.SUDDEN_DEATH)); }
				  if ((mp.getRoom().slotMods[i] & 64) != 0)
				  {  currentBeatmap[i] = currentBeatmap[i].applyMods(new Mods(Mod.DOUBLE_TIME)); }
				  if ((mp.getRoom().slotMods[i] & 128) != 0)
				  {  currentBeatmap[i] = currentBeatmap[i].applyMods(new Mods(Mod.RELAX)); }
				  if ((mp.getRoom().slotMods[i] & 256) != 0)
				  {  currentBeatmap[i] = currentBeatmap[i].applyMods(new Mods(Mod.HALF_TIME)); }
				  if ((mp.getRoom().slotMods[i] & 576) != 0)
				  {  currentBeatmap[i] = currentBeatmap[i].applyMods(new Mods(Mod.NIGHTCORE)); }
				  if ((mp.getRoom().slotMods[i] & 1024) != 0)
				  {  currentBeatmap[i] = currentBeatmap[i].applyMods(new Mods(Mod.FLASHLIGHT)); }
				  if ((mp.getRoom().slotMods[i] & 2048) != 0)
				  {  currentBeatmap[i] = currentBeatmap[i].applyMods(new Mods(Mod.AUTOPLAY)); }
				  if ((mp.getRoom().slotMods[i] & 4096) != 0)
				  {  currentBeatmap[i] = currentBeatmap[i].applyMods(new Mods(Mod.SPUN_OUT)); }
				  if ((mp.getRoom().slotMods[i] & 8192) != 0)
				  {  currentBeatmap[i] = currentBeatmap[i].applyMods(new Mods(Mod.AUTOPILOT)); }
			  }
		}
		
	}

	public void onBeatmapAdded(Beatmap beatmap, int bID) {
		bot.bancho.sendMessage("#multiplayer", String.format("Added %s - %s [%s] mapped by %s",
				beatmap.getArtist(), beatmap.getTitle(), beatmap.getVersion(), beatmap.getCreator()));
		if (bot.beatmaps.getBeatmap() == null) {
			MultiplayerHandler mp = bot.bancho.getMultiplayerHandler();
			mp.setBeatmap(bot.beatmaps.nextBeatmap());
			  try {
					RequestConfig defaultRequestConfig = RequestConfig.custom()
						    .setSocketTimeout(10000)
						    .setConnectTimeout(10000)
						    .setConnectionRequestTimeout(10000)
						    .build();
					
					HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
					URI uri = new URIBuilder()
							.setScheme("http")
							.setHost("osu.ppy.sh")
							.setPath("/osu/"+bID)
							.build();
					HttpGet request = new HttpGet(uri);
					HttpResponse response = httpClient.execute(request);
					InputStream content = response.getEntity().getContent();
					//String stringContent = IOUtils.toString(content, "UTF-8");
					BeatmapParser parser = new BeatmapParser();
					lt.ekgame.beatmap_analyzer.Beatmap cbp = parser.parse(content);
					for (int i = 0; i < 16; i++)  {
						currentBeatmap[i] = cbp;
					}
					Performance perf = cbp.getPerformance(cbp.getMaxCombo(),0,0,0);
					long ssNOMOD = ((long) perf.getPerformance());
					lt.ekgame.beatmap_analyzer.Beatmap cbp2 = cbp;
					lt.ekgame.beatmap_analyzer.Beatmap cbp3 = cbp;
			  		lt.ekgame.beatmap_analyzer.Beatmap cbp4 = cbp;

					cbp2 = cbp2.applyMods(new Mods(Mod.HIDDEN));
					Performance perf2 = cbp2.getPerformance(cbp.getMaxCombo(),0,0,0);
					long ssHIDDEN = ((long) perf2.getPerformance());
					cbp3 = cbp3.applyMods(new Mods(Mod.HARDROCK));
					Performance perf3 = cbp3.getPerformance(cbp.getMaxCombo(),0,0,0);
					long ssHR = ((long) perf3.getPerformance());
					cbp4 = cbp4.applyMods(new Mods(Mod.HIDDEN,Mod.HARDROCK));
					Performance perf4 = cbp4.getPerformance(cbp.getMaxCombo(),0,0,0);
					long ssHDHR = ((long) perf4.getPerformance());
					bot.bancho.sendMessage("#multiplayer","NOMOD: "+ssNOMOD+"pp || HD: "+ssHIDDEN+"pp || HR: "+ssHR+"pp || HDHR: "+ssHDHR+"pp");
					
			  }	catch ( JSONException | IOException | URISyntaxException | BeatmapException e) {
					e.printStackTrace();
					bot.bancho.sendMessage("#multiplayer", "Error Parsing beatmap");
		 }
		}
	}
	
	public void tryStart() {
		MultiplayerHandler mp = bot.bancho.getMultiplayerHandler();
		MultiplayerRoom room = mp.getRoom();
		byte[] status = room.slotStatus;
		slotsTaken = 0;
		int slotsReady = 0;
		String statuses = "";
		for (int i = 0; i < 16; i++)  {
			statuses += status[i] + " ";
			if (status[i] != 1 && status[i] != 2) {
				if (room.slotId[i] != bot.bancho.getClientHandler().getUserId()) {
					slotsTaken++;
					if (status[i] == 8)
						slotsReady++;
				}
			}
		}
		System.out.println(statuses);
		if (slotsTaken > 0 && ((double)slotsReady)/((double)slotsTaken) > 0.7) {
			bot.bancho.sendMessage("#multiplayer", String.format("%d/%d people are ready - starting the game.", slotsReady, slotsTaken));
			startGame();
		} else {
			bot.bancho.sendMessage("#multiplayer", String.format("%d/%d people are ready - extending wait time.", slotsReady, slotsTaken));
			timer.resetTimer();
		}
	}
	
	public void resetBeatmaps() {
		bot.beatmaps.reset();
		MultiplayerHandler mp = bot.bancho.getMultiplayerHandler();
		mp.setBeatmap(bot.beatmaps.nextBeatmap());
	}

	public class TimerThread extends Thread {
		
		private RoomHandler handler;
		
		private boolean stopped = false;
		private long prevTime = System.currentTimeMillis();
		private long startTime;
		private long startAfter = 3*60*1000;
		
		public TimerThread(RoomHandler handler) {
			this.handler = handler;
		}
		
		public void stopTimer() {
			stopped = true;
		}
		
		public void  skipEvents() {
			startTime = System.currentTimeMillis() - 5000;
		}
		
		public void resetTimer() {
			startTime = System.currentTimeMillis() + startAfter + 200;
		}
		
		private void sendMessage(String message) {
			handler.bot.bancho.sendMessage("#multiplayer", message);
		}
		
		public void run() {
			resetTimer();
			while (!stopped) {
				//System.out.println("tick");
				long currTime = System.currentTimeMillis();
				long min3mark = startTime - 3*60*1000;
				long min2mark = startTime - 2*60*1000;
				long min1mark = startTime - 1*60*1000;
				long sec10mark = startTime - 10*1000;
				if (currTime >= min3mark && prevTime<min3mark) {
					sendMessage("Starting in 3 minutes.");
				}
				if (currTime >= min2mark && prevTime<min2mark) {
					sendMessage("Starting in 2 minutes.");
				}
				if (currTime >= min1mark && prevTime<min1mark) {
					sendMessage("Starting in 1 minute.");
				}
				if (currTime >= sec10mark && prevTime<sec10mark) {
					sendMessage("Starting in 10 seconds.");
				}
				if (currTime >= startTime && prevTime<=startTime) {
					handler.tryStart();
				}
				try {
					Thread.sleep(1000);
				} catch (Exception e) {}
				prevTime = currTime;
			}
		}
	}
}
