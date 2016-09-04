package lt.ekgame.autohost;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import lt.ekgame.bancho.api.units.Beatmap;
import lt.ekgame.bancho.client.CommandExecutor;
import lt.ekgame.bancho.client.MultiplayerHandler;

public class CommandsRoom implements CommandExecutor {
	
	private AutoHost bot;
	private Pattern beatmapMatcher = Pattern.compile("((https?:\\/\\/)?osu\\.ppy\\.sh\\/b\\/)(\\d*)");
	private String osuApiKey;
	
	public CommandsRoom(AutoHost bot, String osuApiKey) {
		this.bot = bot;
		this.osuApiKey = osuApiKey;
	}

	@Override
	public boolean accept(String channel, String sender) {
		return channel.equals("#multiplayer");
	}

	@Override
	public void handle(String channel, String sender, int userId, String label, List<String> args) {
		MultiplayerHandler mp = bot.bancho.getMultiplayerHandler();
		if (label.equals("leave") && bot.perms.isOperator(userId)) {
			bot.bancho.sendMessage(channel, "Goodbye!");
			bot.roomHandler.timer.stopTimer();
			mp.leaveRoom();
		}
		
		if (label.equals("reset") && bot.perms.isOperator(userId)) {
			bot.roomHandler.resetBeatmaps();
		}
		
		if (label.equals("voteskip")) {
			bot.roomHandler.registerVoteSkip(userId,sender);
			//bot.bancho.sendMessage(channel, sender + " voted to skip the song!");
			
		}
		
		if (label.equals("skip") && bot.perms.isOperator(userId)) {
			if (mp.isHost()) {
				mp.setBeatmap(bot.beatmaps.nextBeatmap());
				bot.roomHandler.onBeatmapChange();
			}
		}
		
		if (label.equals("freemods") && bot.perms.isOperator(userId)) {
			if (mp.isHost()) {
				mp.setFreeMods(!mp.isFreeModsEnabled());
			}
		}
		
		if (label.equals("rename") && bot.perms.isOperator(userId)) {
			String search = args.get(0);
			for (int i=1; i < args.size(); i++) {
				search = search + " " + args.get(i);
			};
			mp.setRoomName(search);
			bot.bancho.sendMessage(channel, "Lobby name now is: "+search);
		}
		
		if (label.equals("mindiff") && bot.perms.isOperator(userId)) {
			AutoHost.instance.settings.minDifficulty = Double.parseDouble(args.get(0));
			bot.bancho.sendMessage(channel, "New minimum difficulty now is "+args.get(0)+"*");
		}
		
		if (label.equals("maxdiff") && bot.perms.isOperator(userId)) {
			AutoHost.instance.settings.maxDifficulty = Double.parseDouble(args.get(0));
			bot.bancho.sendMessage(channel, "New maximum difficulty now is "+args.get(0)+"*");
		}

		
		if (label.equals("start") && bot.perms.isOperator(userId)) {
			if (mp.isHost()) {
				mp.startGame();
			}
		}
		
		if (label.equals("cookie")) {
			String response = "Saddly this game hasnt got a cookie emoji ¯\\_(O.O)_/¯";
			bot.bancho.sendMessage(channel, sender+": "+response);
		}
		
		if (label.equals("info")) {
			bot.bancho.sendMessage(channel, AutoHost.instance.settings.infoText);
		}
		
		if (label.equals("searchsong") && args.size() > 0) {
			/*
	        for(int i = 0; i < args.size() ; i++) {
	        	bot.bancho.sendMessage(sender, ""+args.get(i));;
	        }
	        */
			
			try { 
			RequestConfig defaultRequestConfig = RequestConfig.custom()
				    .setSocketTimeout(10000)
				    .setConnectTimeout(10000)
				    .setConnectionRequestTimeout(10000)
				    .build();
			HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
			String search = args.get(0);
			for (int i=1; i < args.size(); i++) {
				search = search + " " + args.get(i);
			};
			Settings settings = AutoHost.instance.settings;
			URI uri = new URIBuilder()
					.setScheme("http")
					.setHost("osusearch.com")
					.setPath("/query/")
					.setParameter("title", search)
					.setParameter("statuses", "Ranked")
					.setParameter("modes", "Standard")
					.setParameter("order", "play_count")
					.setParameter("star", "( "+ settings.minDifficulty + "," + settings.maxDifficulty + ")")
					.build(); 
			//bot.bancho.sendMessage(sender, search);
			//settings.minDifficulty settings.maxDifficulty
			//System.out.println(uri);
			HttpGet request = new HttpGet(uri);
			HttpResponse response = httpClient.execute(request);
			InputStream content = response.getEntity().getContent();
			String stringContent = IOUtils.toString(content, "UTF-8");
			JSONObject obj = new JSONObject(stringContent);
			JSONArray Info = obj.getJSONArray("beatmaps");
			//Beatmap Maps = gson.fromJson(stringContent);
			int size = 0;
			for (int i=0; i < Info.length(); i++) {
				//System.out.println( ""+Info.get(i));
				size = size + 1;
			};
			//bot.bancho.sendMessage(sender, ""+size);
			if ( size > 1 ) {
				if (size > 3) {
				bot.bancho.sendMessage(channel,sender + ": "+"Found "+size+" maps, please be more precise!");
				} else if (size < 4) {
					bot.bancho.sendMessage(channel,sender + ": "+"Please retry being more specific from the one of the following maps and use !add:");
					String returnMaps = "";
					for (int i=0; i < Info.length(); i++) {
						String str = ""+Info.get(i);
						JSONObject beatmap = new JSONObject(str);
						int id = beatmap.getInt("beatmap_id");
						String artist = beatmap.getString("artist");
						String title = beatmap.getString("title");
						String difficulty = beatmap.getString("difficulty_name");
						String result = artist + " - " + title + " ("+difficulty+")";
						String urllink = "http://osu.ppy.sh/b/"+id;
						returnMaps = returnMaps+" || ["+urllink+" "+result+"]"; 
					};
					bot.bancho.sendMessage(channel,sender + ": "+returnMaps);
				}
			}		
			else if (size == 0){
				bot.bancho.sendMessage(channel,sender + ": 0 beatmaps found in current difficulty range!");
			}
			else if (size == 1) {
				//bot.bancho.sendMessage(sender, "Correct!");
				//int result = Info.getInt(1);
				String str = ""+Info.get(0);
				JSONObject beatmap = new JSONObject(str);
				String artist = beatmap.getString("artist");
				String title = beatmap.getString("title");
				String difficulty = beatmap.getString("difficulty_name");
				String rating = BigDecimal.valueOf(Math.round( (beatmap.getDouble("difficulty")*100d) )/100d).toPlainString();
				int bID = beatmap.getInt("beatmap_id");
				String result = artist + " - " + title + " [ "+difficulty+" ] - [ "+rating+"* ]";
				String result2 = "[http://osu.ppy.sh/b/"+bID+" Link]";
				bot.bancho.sendMessage(channel,sender + ": "+result + " || " + result2);
				
				// BEATMAP FOUND >> ADD
				getBeatmap(bID, (obje) -> {
					if (obje == null) {
						bot.bancho.sendMessage(channel, sender + ": Beatmap not found.");
					} else {
						int approval = obje.getInt("approved");
						int length = obje.getInt("total_length");
						int mode = obje.getInt("mode");
						
						boolean matchingGamemode = mode == settings.gamemode;
						boolean matchingLength = length <= settings.maxLength;
						boolean matchingApproval = settings.allowGraveyard ? true : (approval >= 0 &&  approval <= 2);
						
						if (!matchingGamemode) {
							bot.bancho.sendMessage(channel, sender + ": This gamemode is not allowed.");
						}
						else if (!matchingLength) {
							bot.bancho.sendMessage(channel, sender + ": This map is too long.");
						}
						else if (!matchingApproval) {
							bot.bancho.sendMessage(channel, sender + ": Graveyarded maps not allowed for search. Use direct link please.");
						}
						else {
							String creator = obje.getString("creator");
							String version = obje.getString("version");
							String beatmapMD5 = obje.getString("file_md5");
							Beatmap bp = new Beatmap(artist, title, version, creator, beatmapMD5, bID);
							bp.RequestedBy = userId;
							if (bot.beatmaps.inQueue(bp)) {
								bot.bancho.sendMessage(channel, sender + ": This beatmap is already in the queue.");
							} else if (bot.beatmaps.recentlyPlayed(bp, 30)) {
								bot.bancho.sendMessage(channel, sender + ": This beatmap has been played recently. PM me !help for more info.");
							}
							else
							{
								if (bot.beatmaps.hasRequested(userId)) 
									{
									bot.bancho.sendMessage(channel, sender + ": You have already requested a beatmap");
									}
									else
									{
									bot.beatmaps.push(bp);
									bot.roomHandler.onBeatmapAdded(bp,bp.getId());
									}
								}
							}
						}
				});

			//bot.bancho.sendMessage(sender, result);
			}
			} catch ( JSONException | URISyntaxException | IOException e) {
				e.printStackTrace();
				bot.bancho.sendMessage(sender, sender + ": Error");
			}
		}
		
		if (label.equals("add") && args.size() > 0) {
			Matcher matcher = beatmapMatcher.matcher(args.get(0));
			if (matcher.find()) {
				//bot.bancho.sendMessage(channel, "Analyzing beatmap #" + matcher.group(3) + ". Please wait...");
				int beatmapId = Integer.parseInt(matcher.group(3));
				try {
					// Is this JavaScript? This is probably JavaScript.
					getBeatmap(beatmapId, (obj) -> {
						if (obj == null) {
							bot.bancho.sendMessage(channel, sender + ": Beatmap not found.");
						} else {
							int approval = obj.getInt("approved");
							double difficulty = obj.getDouble("difficultyrating");
							int length = obj.getInt("total_length");
							int mode = obj.getInt("mode");
							
							Settings settings = AutoHost.instance.settings;
							boolean matchingGamemode = mode == settings.gamemode;
							boolean matchingDifficulty = difficulty >= settings.minDifficulty && difficulty <= settings.maxDifficulty;
							boolean matchingLength = length <= settings.maxLength;
							boolean matchingApproval = settings.allowGraveyard ? true : (approval >= 0 &&  approval <= 2);
							
							if (!matchingGamemode) {
								bot.bancho.sendMessage(channel, sender + ": This gamemode is not allowed.");
							}
							else if (!matchingDifficulty) {
								bot.bancho.sendMessage(channel, sender + ": Invalid difficulty. Must be between " + settings.minDifficulty + " and " + settings.maxDifficulty + ".");
							}
							else if (!matchingLength) {
								bot.bancho.sendMessage(channel, sender + ": This map is too long.");
							}
							else if (!matchingApproval) {
								bot.bancho.sendMessage(channel, sender + ": Graveyarded maps not allowed.");
							}
							else {
								String title = obj.getString("title");
								String artist = obj.getString("artist");
								String creator = obj.getString("creator");
								String version = obj.getString("version");
								String beatmapMD5 = obj.getString("file_md5");
								Beatmap beatmap = new Beatmap(artist, title, version, creator, beatmapMD5, beatmapId);
								if (bot.beatmaps.inQueue(beatmap)) {
									bot.bancho.sendMessage(channel, sender + ": This beatmap is already in the queue.");
								} else if (bot.beatmaps.recentlyPlayed(beatmap, 30)) {
									bot.bancho.sendMessage(channel, sender + ": This beatmap has been played recently. PM me !help for more info.");
								}
								else{
								if (bot.beatmaps.hasRequested(userId)) 
									{
									bot.bancho.sendMessage(channel, sender + ": You have already requested a beatmap");
									}
									else
									{
									bot.beatmaps.push(beatmap);
									bot.roomHandler.onBeatmapAdded(beatmap, beatmap.getId());
									}
								}
							}
						}
					});
				} catch (JSONException | URISyntaxException | IOException e) {
					e.printStackTrace();
					bot.bancho.sendMessage(channel, sender + ": osu! servers seem to be a little slow right now, try again later.");
				}
			} else {
				//bot.bancho.sendMessage(channel, "not found");
			}
		}
	}
	
	void getBeatmap(int beatmapId, Consumer<JSONObject> callback) throws URISyntaxException, ClientProtocolException, IOException {
		RequestConfig defaultRequestConfig = RequestConfig.custom()
			    .setSocketTimeout(10000)
			    .setConnectTimeout(10000)
			    .setConnectionRequestTimeout(10000)
			    .build();
		
		HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
		URI uri = new URIBuilder()
				.setScheme("http")
				.setHost("osu.ppy.sh")
				.setPath("/api/get_beatmaps")
				.setParameter("k", osuApiKey)
				.setParameter("b", ""+beatmapId)
				.build();
		HttpGet request = new HttpGet(uri);
		HttpResponse response = httpClient.execute(request);
		InputStream content = response.getEntity().getContent();
		String stringContent = IOUtils.toString(content, "UTF-8"); 
		JSONArray array = new JSONArray(stringContent);
		callback.accept(array.length() > 0 ? (JSONObject)array.get(0) : null);
	}
}
