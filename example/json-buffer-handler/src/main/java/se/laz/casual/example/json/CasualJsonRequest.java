package se.laz.casual.example.json;

import com.google.gson.JsonElement;

import java.util.List;

public record CasualJsonRequest(List<JsonElement> params)
{
    public CasualJsonRequest
    {
        params = params == null ? List.of() : List.copyOf(params);
    }
}
