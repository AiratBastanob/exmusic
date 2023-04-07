package com.example.myapplication;

import java.util.Random;

public class RandomMusic
{
    private Integer idMusic,RandomIdMusic;
    private Boolean check=true;
    protected Integer SetRandomId(Integer LastIdMusic)
    {
        idMusic=LastIdMusic;
        Random r = new Random();
        while (check)
        {
            RandomIdMusic = r.nextInt(5);
            if(RandomIdMusic.equals(idMusic))
            {

            }
            else
            {
                check=false;
                idMusic=RandomIdMusic;
            }
        }
        check=true;
        return idMusic;
    }
}
