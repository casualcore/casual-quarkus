package se.laz.casual.example;

import jakarta.enterprise.context.ApplicationScoped;
import se.laz.casual.api.service.CasualService;

import java.util.Arrays;

@ApplicationScoped
public class SumServiceImpl implements SumService
{
    @Override
    @CasualService(name = "sum", category = "example")
    public int sum(int[] numbers)
    {
        return (numbers == null) ? 0 : Arrays.stream(numbers).sum();
    }
}
