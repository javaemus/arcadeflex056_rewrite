package WIP2.arcadeflex036;

import static WIP2.common.libc.cstring.*;

import WIP2.arcadeflex056.settings;
import static WIP2.arcadeflex036.config.parse_cmdline;
import static WIP2.arcadeflex036.fronthlp.frontend_help;
import static WIP2.common.libc.cstdio.*;
import static WIP2.arcadeflex036.ticker.init_ticker;
import static WIP2.mame056.driver.drivers;
import static WIP2.mame056.mame.*;

/**
 * This file is relative to msdos.c in mame_old
 *
 * @author george
 */
public class osdepend {
    static FILE errorlog=null;
    public static int ignorecfg;
    public static UrlDownloadProgress dlprogress;
    /* put here anything you need to do when the program is started. Return 0 if */
    /* initialization was successful, nonzero otherwise. */

    public static int osd_init() {
        /*TODO*///	if (msdos_init_sound())
    /*TODO*///		return 1;
    /*TODO*///	msdos_init_input();
        return 0;
    }

    /* put here cleanup routines to be executed when the program is terminated. */
    public static void osd_exit() {
        /*TODO*///	msdos_shutdown_sound();
    /*TODO*///	msdos_shutdown_input();
    }

    /* fuzzy string compare, compare short string against long string        */
    /* e.g. astdel == "Asteroids Deluxe". The return code is the fuzz index, */
    /* we simply count the gaps between maching chars.                       */
    /*TODO*///int fuzzycmp (const char *s, const char *l)
    /*TODO*///{
    /*TODO*///	int gaps = 0;
    /*TODO*///	int match = 0;
    /*TODO*///	int last = 1;
    /*TODO*///
    /*TODO*///	for (; *s && *l; l++)
    /*TODO*///	{
    /*TODO*///		if (*s == *l)
    /*TODO*///			match = 1;
    /*TODO*///		else if (*s >= 'a' && *s <= 'z' && (*s - 'a') == (*l - 'A'))
    /*TODO*///			match = 1;
    /*TODO*///		else if (*s >= 'A' && *s <= 'Z' && (*s - 'A') == (*l - 'a'))
    /*TODO*///			match = 1;
    /*TODO*///		else
    /*TODO*///			match = 0;
    /*TODO*///
    /*TODO*///		if (match)
    /*TODO*///			s++;
    /*TODO*///
    /*TODO*///		if (match != last)
    /*TODO*///		{
    /*TODO*///			last = match;
    /*TODO*///			if (!match)
    /*TODO*///				gaps++;
    /*TODO*///		}
    /*TODO*///	}
    /*TODO*///
    /*TODO*///	/* penalty if short string does not completely fit in */
    /*TODO*///	for (; *s; s++)
    /*TODO*///		gaps++;
    /*TODO*///
    /*TODO*///	return gaps;
    /*TODO*///}
    /*TODO*///
    public static int main(int argc, String[] argv) {
        dlprogress = new UrlDownloadProgress();
        dlprogress.setVersion("arcadeflex version: " + settings.version);
        dlprogress.setVisible(true);
        int res, i, j = 0, game_index;
        String playbackname = "";
        /* these two are not available in mame_old.cfg */
        ignorecfg = 0;
        errorlog =  null;

        game_index = -1;
        for (i = 1; i < argc; i++) /* V.V_121997 */ {
            if (stricmp(argv[i], "-ignorecfg") == 0) {
                ignorecfg = 1;
            }
            if (stricmp(argv[i], "-log") == 0) {
                errorlog =  fopen("error.log", "wa");
            }
            if (stricmp(argv[i], "-playback") == 0) {
                i++;
                if (i < argc) /* point to inp file name */ {
                    playbackname = argv[i];
                }
            }
        }

        /* check for frontend options */
        res = frontend_help(argc, argv);
        /* if frontend options were used, return */
        if (res != 1234) {
            return res;
        }
        /*TODO*///
    /*TODO*///	/* Initialize the audio library */
    /*TODO*///	if (msdos_init_seal())
    /*TODO*///	{
    /*TODO*///		printf ("Unable to initialize SEAL\n");
    /*TODO*///		return (1);
    /*TODO*///	}
    /*TODO*///
        init_ticker();	/* after Allegro init because we use cpu_cpuid */
        /*TODO*///
    /*TODO*///    /* handle playback which is not available in mame_old.cfg */
    /*TODO*///	init_inpdir(); /* Init input directory for opening .inp for playback */
    /*TODO*///
    /*TODO*///    if (playbackname != NULL)
    /*TODO*///        options.playback = osd_fopen(playbackname,0,OSD_FILETYPE_INPUTLOG,0);
    /*TODO*///
    /*TODO*///    /* check for game name embedded in .inp header */
    /*TODO*///    if (options.playback)
    /*TODO*///    {
    /*TODO*///        INP_HEADER inp_header;
    /*TODO*///
    /*TODO*///        /* read playback header */
    /*TODO*///        osd_fread(options.playback, &inp_header, sizeof(INP_HEADER));
    /*TODO*///
    /*TODO*///        if (!isalnum(inp_header.name[0])) /* If first byte is not alpha-numeric */
    /*TODO*///            osd_fseek(options.playback, 0, SEEK_SET); /* old .inp file - no header */
    /*TODO*///        else
    /*TODO*///        {
    /*TODO*///            for (i = 0; (drivers_old[i] != 0); i++) /* find game and play it */
    /*TODO*///			{
    /*TODO*///                if (strcmp(drivers_old[i]->name, inp_header.name) == 0)
    /*TODO*///                {
    /*TODO*///                    game_index = i;
    /*TODO*///                    printf("Playing back previously recorded game %s (%s) [press return]\n",
    /*TODO*///                        drivers_old[game_index]->name,drivers_old[game_index]->description);
    /*TODO*///                    getchar();
    /*TODO*///                    break;
    /*TODO*///                }
    /*TODO*///            }
    /*TODO*///        }
    /*TODO*///    }
    /*TODO*///
    	/* If not playing back a new .inp file */

        if (game_index == -1) {
            /* take the first commandline argument without "-" as the game name */
            for (j = 1; j < argc; j++) {
                if (!argv[j].startsWith("-")) {
                    break;//if (argv[j][0] != '-') break; (original c code,conversion seems ok)
                }
            }
            /* do we have a driver for this? */
            for (i = 0; drivers[i] != null && (game_index == -1); i++) {
                if (stricmp(argv[j], drivers[i].name) == 0) {
                    game_index = i;
                    dlprogress.setRomName("loading game: "+drivers[i].name);
                    break;
                }
            }
            /*TODO*///            /* educated guess on what the user wants to play */
    /*TODO*///            if (game_index == -1)
    /*TODO*///            {
    /*TODO*///                int fuzz = 9999; /* best fuzz factor so far */
    /*TODO*///
    /*TODO*///                for (i = 0; (drivers_old[i] != 0); i++)
    /*TODO*///                {
    /*TODO*///                    int tmp;
    /*TODO*///                    tmp = fuzzycmp(argv[j], drivers_old[i]->description);
    /*TODO*///                    /* continue if the fuzz index is worse */
    /*TODO*///                    if (tmp > fuzz)
    /*TODO*///                        continue;
    /*TODO*///
    /*TODO*///                    /* on equal fuzz index, we prefer working, original games */
    /*TODO*///                    if (tmp == fuzz)
    /*TODO*///                    {
    /*TODO*///						/* game is a clone */
    /*TODO*///						if (drivers_old[i]->clone_of != 0
    /*TODO*///								&& !(drivers_old[i]->clone_of->flags & NOT_A_DRIVER))
    /*TODO*///                        {
    /*TODO*///                            /* if the game we already found works, why bother. */
    /*TODO*///                            /* and broken clones aren't very helpful either */
    /*TODO*///                            if ((!drivers_old[game_index]->flags & GAME_NOT_WORKING) ||
    /*TODO*///                                (drivers_old[i]->flags & GAME_NOT_WORKING))
    /*TODO*///                                continue;
    /*TODO*///                        }
    /*TODO*///                        else continue;
    /*TODO*///                    }
    /*TODO*///
    /*TODO*///                    /* we found a better match */
    /*TODO*///                    game_index = i;
    /*TODO*///                    fuzz = tmp;
    /*TODO*///                }
    /*TODO*///
    /*TODO*///                if (game_index != -1)
    /*TODO*///                    printf("fuzzy name compare, running %s\n",drivers_old[game_index]->name);
    /*TODO*///            }
    /*TODO*///
            if (game_index == -1) {
                printf("Game \"%s\" not supported\n", argv[j]);
                dlprogress.setRomName("error in game name or game not supported");
                return 1;
            }
        }
        /*TODO*///
    /*TODO*///
	/* parse generic (os-independent) options */
        parse_cmdline(argc, argv, game_index);
        /*TODO*///
    /*TODO*///{	/* Mish:  I need sample rate initialised _before_ rom loading for optional rom regions */
    /*TODO*///	extern int soundcard;
    /*TODO*///
    /*TODO*///	if (soundcard == 0) {    /* silence, this would be -1 if unknown in which case all roms are loaded */
    /*TODO*///		Machine->sample_rate = 0; /* update the Machine structure to show that sound_old is disabled */
    /*TODO*///		options.samplerate=0;
    /*TODO*///	}
    /*TODO*///}
    /*TODO*///
    /*TODO*///	/* handle record which is not available in mame_old.cfg */
    /*TODO*///	for (i = 1; i < argc; i++)
    /*TODO*///	{
    /*TODO*///		if (stricmp(argv[i],"-record") == 0)
    /*TODO*///		{
    /*TODO*///            i++;
    /*TODO*///			if (i < argc)
    /*TODO*///				options.record = osd_fopen(argv[i],0,OSD_FILETYPE_INPUTLOG,1);
    /*TODO*///		}
    /*TODO*///	}
    /*TODO*///
    /*TODO*///    if (options.record)
    /*TODO*///    {
    /*TODO*///        INP_HEADER inp_header;
    /*TODO*///
    /*TODO*///        memset(&inp_header, '\0', sizeof(INP_HEADER));
    /*TODO*///        strcpy(inp_header.name, drivers_old[game_index]->name);
    /*TODO*///        /* MAME32 stores the MAME version numbers at bytes 9 - 11
    /*TODO*///         * MAME DOS keeps this information in a string, the
    /*TODO*///         * Windows code defines them in the Makefile.
    /*TODO*///         */
    /*TODO*///        /*
    /*TODO*///        inp_header.version[0] = 0;
    /*TODO*///        inp_header.version[1] = VERSION;
    /*TODO*///        inp_header.version[2] = BETA_VERSION;
    /*TODO*///        */
    /*TODO*///        osd_fwrite(options.record, &inp_header, sizeof(INP_HEADER));
    /*TODO*///    }
    /*TODO*///
        /* go for it */
        res = run_game(game_index);
        /*TODO*///
    	/* close open files */
        if (errorlog != null) {
            fclose(errorlog);
        }
        /*TODO*///	if (options.playback) osd_fclose (options.playback);
    /*TODO*///	if (options.record)   osd_fclose (options.record);
    /*TODO*///
        return res;
    }
    public static void logerror(String str, Object... arguments) {
        if (errorlog != null) {
            fprintf(errorlog, str, arguments);
        }
    }
}
