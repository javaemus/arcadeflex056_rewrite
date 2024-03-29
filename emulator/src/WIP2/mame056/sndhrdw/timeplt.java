/**
 * ported to v0.56
 * ported to v0.37b7
 */
package WIP2.mame056.sndhrdw;

import static WIP2.arcadeflex056.fucPtr.*;

import static WIP2.mame056.memoryH.*;
import static WIP2.mame056.sndintrf.*;
import static WIP2.mame056.sound.ay8910.*;
import static WIP2.mame056.sound.ay8910H.*;
import static WIP2.mame056.sound.mixerH.*;
import static WIP2.mame056.sound.streams.*;
import static WIP2.mame056.cpuexec.*;

public class timeplt {

    /* The timer clock which feeds the upper 4 bits of    					*/
 /* AY-3-8910 port A is based on the same clock        					*/
 /* feeding the sound CPU Z80.  It is a divide by      					*/
 /* 5120, formed by a standard divide by 512,        					*/
 /* followed by a divide by 10 using a 4 bit           					*/
 /* bi-quinary count sequence. (See LS90 data sheet    					*/
 /* for an example).                                   					*/
 /*																		*/
 /* Bit 4 comes from the output of the divide by 1024  					*/
 /*       0, 1, 0, 1, 0, 1, 0, 1, 0, 1									*/
 /* Bit 5 comes from the QC output of the LS90 producing a sequence of	*/
 /* 		 0, 0, 1, 1, 0, 0, 1, 1, 1, 0									*/
 /* Bit 6 comes from the QD output of the LS90 producing a sequence of	*/
 /*		 0, 0, 0, 0, 1, 0, 0, 0, 0, 1									*/
 /* Bit 7 comes from the QA output of the LS90 producing a sequence of	*/
 /*		 0, 0, 0, 0, 0, 1, 1, 1, 1, 1			 						*/
    static int timeplt_timer[]
            = {
                0x00, 0x10, 0x20, 0x30, 0x40, 0x90, 0xa0, 0xb0, 0xa0, 0xd0
            };
    /* need to protect from totalcycles overflow */
    static int last_totalcycles = 0;

    /* number of Z80 clock cycles to count */
    static int clock;
    public static ReadHandlerPtr timeplt_portB_r = new ReadHandlerPtr() {
        public int handler(int offset) {

            int current_totalcycles;

            current_totalcycles = cpu_gettotalcycles();
            clock = (clock + (current_totalcycles - last_totalcycles)) % 5120;

            last_totalcycles = current_totalcycles;

            return timeplt_timer[clock / 512];
        }
    };

    static void filter_w(int chip, int channel, int data) {
        int C = 0;

        if ((data & 1) != 0) {
            C += 220000;
            /* 220000pF = 0.220uF */
        }
        if ((data & 2) != 0) {
            C += 47000;
            /*  47000pF = 0.047uF */
        }
        set_RC_filter(3 * chip + channel, 1000, 5100, 0, C);
    }

    public static WriteHandlerPtr timeplt_filter_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            filter_w(0, 0, (offset >> 6) & 3);
            filter_w(0, 1, (offset >> 8) & 3);
            filter_w(0, 2, (offset >> 10) & 3);
            filter_w(1, 0, (offset >> 0) & 3);
            filter_w(1, 1, (offset >> 2) & 3);
            filter_w(1, 2, (offset >> 4) & 3);
        }
    };

    static int last;
    public static WriteHandlerPtr timeplt_sh_irqtrigger_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            if (last == 0 && data != 0) {
                /* setting bit 0 low then high triggers IRQ on the sound CPU */
                cpu_cause_interrupt(1, 0xff);
            }

            last = data;
        }
    };
    public static Memory_ReadAddress timeplt_sound_readmem[]
            = {
                new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
                new Memory_ReadAddress(0x0000, 0x1fff, MRA_ROM),
                new Memory_ReadAddress(0x2000, 0x23ff, MRA_RAM),
                new Memory_ReadAddress(0x3000, 0x33ff, MRA_RAM),
                new Memory_ReadAddress(0x4000, 0x4000, AY8910_read_port_0_r),
                new Memory_ReadAddress(0x6000, 0x6000, AY8910_read_port_1_r),
                new Memory_ReadAddress(MEMPORT_MARKER, 0)};

    public static Memory_WriteAddress timeplt_sound_writemem[]
            = {
                new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
                new Memory_WriteAddress(0x0000, 0x1fff, MWA_ROM),
                new Memory_WriteAddress(0x2000, 0x23ff, MWA_RAM),
                new Memory_WriteAddress(0x3000, 0x33ff, MWA_RAM),
                new Memory_WriteAddress(0x4000, 0x4000, AY8910_write_port_0_w),
                new Memory_WriteAddress(0x5000, 0x5000, AY8910_control_port_0_w),
                new Memory_WriteAddress(0x6000, 0x6000, AY8910_write_port_1_w),
                new Memory_WriteAddress(0x7000, 0x7000, AY8910_control_port_1_w),
                new Memory_WriteAddress(0x8000, 0x8fff, timeplt_filter_w),
                new Memory_WriteAddress(MEMPORT_MARKER, 0)};

    public static AY8910interface timeplt_ay8910_interface = new AY8910interface(
            2, /* 2 chips */
            14318180 / 8, /* 1.789772727 MHz */
            new int[]{MIXERG(30, MIXER_GAIN_2x, MIXER_PAN_CENTER), MIXERG(30, MIXER_GAIN_2x, MIXER_PAN_CENTER)},
            new ReadHandlerPtr[]{soundlatch_r, null},
            new ReadHandlerPtr[]{timeplt_portB_r, null},
            new WriteHandlerPtr[]{null, null},
            new WriteHandlerPtr[]{null, null}
    );

}
