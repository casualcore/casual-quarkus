package se.laz.casual.example.json;

import com.google.gson.JsonElement;

public record CasualJsonRequest(JsonElement[] params)
{}
