package WIP2.mame056.sound;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.libc.cstdio.sprintf;
import WIP2.common.ptr.ShortPtr;
import WIP2.common.ptr.UBytePtr;
import static WIP2.mame037b11.sound.fmopl.*;
import static WIP2.mame037b11.sound.fmoplH.*;
import static WIP2.mame056.common.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.sndintrf.*;
import static WIP2.mame056.sndintrfH.*;
import static WIP2.mame056.sound._3812intfH.*;
import static WIP2.mame056.sound.streams.*;
import static WIP2.mame056.timer.*;

public class y8950intf  extends snd_interface {

    static Y8950interface intf = null;
    static int chiptype;
    static int[] stream = new int[MAX_3812];
    static Object[] Timer = new Object[MAX_3812 * 2];
    static FM_OPL[] F3812 = new FM_OPL[MAX_3812];

    public y8950intf() {
        sound_num = SOUND_Y8950;
        name = "Y8950";
    }

    @Override
    public int chips_num(MachineSound msound) {
        return ((Y8950interface) msound.sound_interface).num;
    }

    @Override
    public int chips_clock(MachineSound msound) {
        return ((Y8950interface) msound.sound_interface).baseclock;
    }

    @Override
    public int start(MachineSound msound) {
        chiptype = OPL_TYPE_Y8950;
        if (OPL_sh_start(msound) != 0) {
            return 1;
        }
        /* !!!!! port handler set !!!!! */
 /* !!!!! delta-t memory address set !!!!! */
        return 0;
    }

    public static int OPL_sh_start(MachineSound msound) {

        int i;
        int rate = Machine.sample_rate;

        intf = (Y8950interface) msound.sound_interface;
        if (intf.num > MAX_3812) {
            return 1;
        }

        /* Timer state clear */
        for (i = 0; i < Timer.length; i++) {
            Timer[i] = null;//memset(Timer,0,sizeof(Timer));
        }
        /* stream system initialize */
        for (i = 0; i < intf.num; i++) {
            /* stream setup */
            String name;
            int vol = intf.mixing_level[i];
            /* emulator create */
            F3812[i] = OPLCreate(chiptype, intf.baseclock, rate);
            if (F3812[i] == null) {
                return 1;
            }
            /* stream setup */
            name = sprintf("%s #%d", sound_name(msound), i);
            F3812[i].deltat.memory = new UBytePtr(memory_region((((Y8950interface) (intf)).rom_region[i])));
            F3812[i].deltat.memory_size = memory_region_length(((Y8950interface) (intf)).rom_region[i]);
            stream[i] = stream_init(name, vol, rate, i, Y8950UpdateHandler);
            /* port and keyboard handler */
            OPLSetPortHandler(F3812[i], Y8950PortHandler_w, Y8950PortHandler_r, i);
            OPLSetKeyboardHandler(F3812[i], Y8950KeyboardHandler_w, Y8950KeyboardHandler_r, i);
            /* YM3812 setup */
            OPLSetTimerHandler(F3812[i], TimerHandler, i * 2);
            OPLSetIRQHandler(F3812[i], IRQHandler, i);
            OPLSetUpdateHandler(F3812[i], stream_updateptr, stream[i]);
        }
        return 0;
    }
    public static timer_callback timer_callback_3812 = new timer_callback() {
        public void handler(int param) {
            int n = param >> 1;
            int c = param & 1;
            Timer[param] = 0;
            OPLTimerOver(F3812[n], c);
        }
    };
    public static OPL_TIMERHANDLERPtr TimerHandler = new OPL_TIMERHANDLERPtr() {

        @Override
        public void handler(int c, double period) {
            if (period == 0) {
                /* Reset FM Timer */

                if (Timer[c] != null) {
                    timer_remove(Timer[c]);
                    Timer[c] = 0;
                }
            } else {
                /* Start FM Timer */

                Timer[c] = timer_set(period, c, timer_callback_3812);
            }
        }
    };
    public static OPL_IRQHANDLERPtr IRQHandler = new OPL_IRQHANDLERPtr() {

        @Override
        public void handler(int n, int irq) {
            if (intf.handler == null) {
                return;
            }
            if (intf.handler[n] != null) {
                (intf.handler[n]).handler(irq != 0 ? ASSERT_LINE : CLEAR_LINE);
            }
        }

    };
    public static OPL_UPDATEHANDLERPtr stream_updateptr = new OPL_UPDATEHANDLERPtr() {
        @Override
        public void handler(int param, int min_interval_us) {
            stream_update(param, min_interval_us);
        }
    };

    @Override
    public void stop() {
        Y8950_sh_stop();
    }

    public static void Y8950_sh_stop() {
        int i;

        for (i = 0; i < intf.num; i++) {
            OPLDestroy(F3812[i]);
        }
    }

    @Override
    public void update() {
        //no functionality expected
    }

    @Override
    public void reset() {
        //no functionality expected
    }
    public static ReadHandlerPtr Y8950_read_port_0_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            return OPLRead(F3812[0], 1);
        }
    };
    public static ReadHandlerPtr Y8950_status_port_0_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            return OPLRead(F3812[0], 0);
        }
    };
    public static WriteHandlerPtr Y8950_control_port_0_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            OPLWrite(F3812[0], 0, data);
        }
    };
    public static WriteHandlerPtr Y8950_write_port_0_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            OPLWrite(F3812[0], 1, data);
        }
    };
    public static ReadHandlerPtr Y8950_read_port_1_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            return OPLRead(F3812[1], 1);
        }
    };
    public static ReadHandlerPtr Y8950_status_port_1_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            return OPLRead(F3812[1], 0);
        }
    };
    public static WriteHandlerPtr Y8950_control_port_1_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            OPLWrite(F3812[1], 0, data);
        }
    };
    public static WriteHandlerPtr Y8950_write_port_1_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            OPLWrite(F3812[1], 1, data);
        }
    };
    public static StreamInitPtr Y8950UpdateHandler = new StreamInitPtr() {
        public void handler(int num, ShortPtr buffer, int length) {
            Y8950UpdateOne(F3812[num], buffer, length);
        }
    };
    public static OPL_PORTHANDLER_RPtr Y8950PortHandler_r = new OPL_PORTHANDLER_RPtr() {
        public /*unsigned char*/ int handler(int chip) {
            return (char) ((Y8950interface) intf).portread[chip].handler(chip) & 0xFF;
        }

    };
    public static OPL_PORTHANDLER_WPtr Y8950PortHandler_w = new OPL_PORTHANDLER_WPtr() {
        public void handler(int chip,/*unsigned char*/ int data) {
            ((Y8950interface) intf).portwrite[chip].handler(chip, data & 0xFF);
        }
    };
    public static OPL_PORTHANDLER_RPtr Y8950KeyboardHandler_r = new OPL_PORTHANDLER_RPtr() {
        public /*unsigned char*/ int handler(int chip) {
            return ((Y8950interface) intf).keyboardread[chip].handler(chip) & 0xFF;
        }

    };
    public static OPL_PORTHANDLER_WPtr Y8950KeyboardHandler_w = new OPL_PORTHANDLER_WPtr() {
        public void handler(int chip,/*unsigned char*/ int data) {
            ((Y8950interface) intf).keyboardwrite[chip].handler(chip, data & 0xFF);
        }
    };
}

