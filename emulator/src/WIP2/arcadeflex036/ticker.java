package WIP2.arcadeflex036;

import static WIP2.common.libc.ctime.*;

public class ticker {

    public static long TICKS_PER_SEC;

    public static long ticker() {
        return uclock();
    }

    public static void init_ticker() {
        TICKS_PER_SEC = UCLOCKS_PER_SEC;
    }

}
