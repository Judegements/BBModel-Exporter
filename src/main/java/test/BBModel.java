package test;

import com.google.gson.*;
import java.io.*;
import java.util.*;

public class BBModel {
	public FileReader file;
	public String model_format;
	public String name;
	public String parent;
	public boolean front_gui_light = false;
	public int width;
	public int height;
	public boolean has_namespace = false;
	
	public JsonObject input;
	public List<Texture> textures = new ArrayList<>();
	public List<Element> elements = new ArrayList<>();
	public Display display = null;
	
	public BBModel(FileReader file) {
		this.file = file;
		this.input = JsonParser.parseReader(file).getAsJsonObject();
		this.model_format = input.getAsJsonObject("meta").get("model_format").getAsString();
		this.name = input.get("name").getAsString();
		this.parent = input.has("parent") ? input.get("parent").getAsString() : "";
		this.front_gui_light = input.has("front_gui_light") && input.get("front_gui_light").getAsBoolean();
		this.width = input.getAsJsonObject("resolution").get("width").getAsInt();
		this.height = input.getAsJsonObject("resolution").get("height").getAsInt();
		int txt_index = 0;
		JsonArray texArray = input.getAsJsonArray("textures");
		if (texArray != null) {
			for (JsonElement tl : texArray) {
				JsonObject t = tl.getAsJsonObject();
				textures.add(new Texture(
					txt_index,
					t.has("namespace") ? t.get("namespace").getAsString() : "",
					t.has("folder") ? t.get("folder").getAsString() : "textures",
					t.has("name") ? t.get("name").getAsString() : "texture_" + txt_index,
					t.get("id").getAsString(),
					t.has("particle") && t.get("particle").getAsBoolean(),
					t.get("source").getAsString()
				));
				txt_index++;
			}
		}
		
		for (JsonElement el : input.getAsJsonArray("elements")) {
			elements.add(new Element(el.getAsJsonObject()));
		}
		
		if (input.has("display")) this.display = new Display(input.getAsJsonObject("display"));
	}
	
	public String export() throws IOException {
		JsonObject output = new JsonObject();
		
		if (!parent.isEmpty()) output.addProperty("parent", parent);
		
		JsonArray texture_size = new JsonArray();
		texture_size.add(width);
		texture_size.add(height);
		output.add("texture_size", texture_size);
		
		JsonObject txt_obj = new JsonObject();
		Boolean particle_is_set = false;
		for (Texture t : this.textures) {
			String loc = t.folder + "/" + t.name;
			String file_loc = t.folder + "/" + t.name;
			if (!t.namespace.isEmpty()) {
				loc = t.namespace + ":" + t.folder + "/" + t.name;
				file_loc = t.namespace + "/" + t.folder + "/" + t.name;
				this.has_namespace = true;
			}
			txt_obj.addProperty(t.id, loc);
			if (t.particle == false) {
				txt_obj.addProperty("particle", loc);
				particle_is_set = true;
			}
			
			exportTexture(t.source, "textures/" + file_loc + ".png");
		}
		output.add("textures", txt_obj);
		
		JsonArray e_arr = new JsonArray();
		for (Element el : this.elements) {
			JsonObject ne = new JsonObject();
			ne.add("from", arrayToJson(el.from));
			ne.add("to", arrayToJson(el.to));
			
			if (el.rotation != null && (el.rotation[0] != 0 || el.rotation[1] != 0 || el.rotation[2] != 0)) {
				JsonObject rot = new JsonObject();
				if (el.rotation[0] != 0) {
					rot.addProperty("angle", el.rotation[0]);
					rot.addProperty("axis", "x");
				} else if (el.rotation[1] != 0) {
					rot.addProperty("angle", el.rotation[1]);
					rot.addProperty("axis", "y");
				} else if (el.rotation[2] != 0) {
					rot.addProperty("angle", el.rotation[2]);
					rot.addProperty("axis", "z");
				}
				rot.add("origin", arrayToJson(el.origin));
				ne.add("rotation", rot);
			} else {
				ne.add("origin", arrayToJson(el.origin));
			}
			
			JsonObject facesObj = new JsonObject();
			for (Map.Entry<String, Face> entry : el.faces.entrySet()) {
				JsonObject faceJson = new JsonObject();
				faceJson.add("uv", arrayToJson(entry.getValue().uv));
				faceJson.addProperty("rotation", entry.getValue().rotation);
				faceJson.addProperty("texture", "#" + entry.getValue().texture);
				facesObj.add(entry.getKey(), faceJson);
			}
			ne.add("faces", facesObj);
			e_arr.add(ne);
		}
		output.add("elements", e_arr);
		
		if (front_gui_light) output.addProperty("gui_light", front_gui_light);
		if (this.display != null) output.add("display", this.display.toJson());
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		return gson.toJson(output);
	}
	
	public void exportTexture(String data, String result) {
		if (data == null || data.isEmpty()) return;
		if (data.contains(",")) data = data.split(",")[1];
		try {
			byte[] imageBytes = Base64.getDecoder().decode(data);
			File outputFile = new File(result);
			File parentDir = outputFile.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				parentDir.mkdirs();
			}
			try (OutputStream stream = new FileOutputStream(outputFile)) {
				stream.write(imageBytes);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public JsonArray arrayToJson(float[] arr) {
		JsonArray array = new JsonArray();
		for (float v : arr) {
			array.add(v);
		}
		return array;
	}
	
	public static class Texture {
		public int index;
		public String namespace;
		public String folder;
		public String name;
		public String id;
		public Boolean particle;
		public String source;
		
		public File loc;
		
		public Texture(int index, String namespace, String folder, String name, String id, Boolean particle, String source) {
			this.namespace = namespace;
			this.folder = folder;
			this.name = name;
			this.id = id;
			this.particle = particle;
			this.source = source;
			
			this.loc = new File(namespace + "/" + folder + "/" + name + ".png");
		}
	}
	
	public static class Element {
		public float[] from = null;
		public float[] to = null;
		public float[] origin = null;
		public float[] rotation = null;
		public Map<String, Face> faces = new HashMap<>();
		
		public Element(JsonObject json) {
			this.from = parse(json.getAsJsonArray("from"));
			this.to = parse(json.getAsJsonArray("to"));
			this.origin = json.has("origin") ? parse(json.getAsJsonArray("origin")) : new float[] {0, 0, 0};
			this.rotation = json.has("rotation") ? parse(json.getAsJsonArray("rotation")) : null;
		
			if (json.has("faces")) {
				JsonObject faceObj = json.getAsJsonObject("faces");
				for (Map.Entry<String, JsonElement> entry : faceObj.entrySet()) {
					faces.put(entry.getKey(), new Face(entry.getValue().getAsJsonObject()));
				}
			}
		}
		
		private float[] parse(JsonArray arr) {
			float[] res = new float[arr.size()];
			for (int i = 0; i < arr.size(); i++) {
				res[i] = arr.get(i).getAsFloat();
			}
			return res;
		}
	}
	
	public static class Face {
		public float[] uv;
		public int texture;
		public int rotation;
		
		public Face(JsonObject face) {
			this.uv = face.has("uv") ? parse(face.getAsJsonArray("uv")) : new float[] {0, 0, 16, 16};
			this.texture = face.has("texture") ? face.get("texture").getAsInt() : 0;
			this.rotation = face.has("rotation") ? face.get("rotation").getAsInt() : 0;
		}
		
		private float[] parse(JsonArray arr) {
			float[] res = new float[arr.size()];
			for (int i = 0; i < arr.size(); i++) {
				res[i] = arr.get(i).getAsFloat();
			}
			return res;
		}
	}
	public static class Display {
		public JsonObject thirdperson_righthand = new JsonObject();
		public JsonObject thirdperson_lefthand = new JsonObject();
		public JsonObject firstperson_righthand = new JsonObject();
		public JsonObject firstperson_lefthand = new JsonObject();
		public JsonObject ground = new JsonObject();
		public JsonObject gui = new JsonObject();
		public JsonObject head = new JsonObject();
		public JsonObject fixed = new JsonObject();
	
		public Display(JsonObject obj) {
			if (obj.has("thirdperson_righthand")) this.thirdperson_righthand = obj.getAsJsonObject("thirdperson_righthand");
			if (obj.has("thirdperson_lefthand")) this.thirdperson_lefthand = obj.getAsJsonObject("thirdperson_lefthand");
			if (obj.has("firstperson_righthand")) this.firstperson_righthand = obj.getAsJsonObject("firstperson_righthand");
			if (obj.has("firstperson_lefthand")) this.firstperson_lefthand = obj.getAsJsonObject("firstperson_lefthand");
			if (obj.has("ground")) this.ground = obj.getAsJsonObject("ground");
			if (obj.has("gui")) this.gui = obj.getAsJsonObject("gui");
			if (obj.has("head")) this.head = obj.getAsJsonObject("head");
			if (obj.has("fixed")) this.fixed = obj.getAsJsonObject("fixed");
		}
	
		public JsonObject toJson() {
			JsonObject out = new JsonObject();
			if (thirdperson_righthand.size() > 0) out.add("thirdperson_righthand", thirdperson_righthand);
			if (thirdperson_lefthand.size() > 0) out.add("thirdperson_lefthand", thirdperson_lefthand);
			if (firstperson_righthand.size() > 0) out.add("firstperson_righthand", firstperson_righthand);
			if (firstperson_lefthand.size() > 0) out.add("firstperson_lefthand", firstperson_lefthand);
			if (ground.size() > 0) out.add("ground", ground);
			if (gui.size() > 0) out.add("gui", gui);
			if (head.size() > 0) out.add("head", head);
			if (fixed.size() > 0) out.add("fixed", fixed);
			return out;
		}
	}
}