/**
 * ported to v0.56
 * ported to v0.37b7
 * ported to v0.36
 *
 */
package WIP2.mame056.sndhrdw;

import static WIP2.mame056.timer.*;
import static WIP2.mame056.timerH.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.sndintrf.*;
import static WIP2.mame056.common.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.sound.dac.*;
import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.arcadeflex036.osdepend.*;

public class m72 {

    /*
	
	  The sound CPU runs in interrup mode 0. IRQ is shared by two sources: the
	  YM2151 (bit 4 of the vector), and the main CPU (bit 5).
	  Since the vector can be changed from different contexts (the YM2151 timer
	  callback, the main CPU context, and the sound CPU context), it's important
	  to accurately arbitrate the changes to avoid out-of-order execution. We do
	  that by handling all vector changes in a single timer callback.
	
     */
    public static final int VECTOR_INIT = 0;
    public static final int YM2151_ASSERT = 1;
    public static final int YM2151_CLEAR = 2;
    public static final int Z80_ASSERT = 3;
    public static final int Z80_CLEAR = 4;

    static int irqvector;
    public static timer_callback setvector_callback = new timer_callback() {
        public void handler(int param) {
            switch (param) {
                case VECTOR_INIT:
                    irqvector = 0xff;
                    break;

                case YM2151_ASSERT:
                    irqvector &= 0xef;
                    break;

                case YM2151_CLEAR:
                    irqvector |= 0x10;
                    break;

                case Z80_ASSERT:
                    irqvector &= 0xdf;
                    break;

                case Z80_CLEAR:
                    irqvector |= 0x20;
                    break;
            }

            if (irqvector == 0) {
                logerror("You didn't call m72_init_sound()\n");
            }

            cpu_irq_line_vector_w(1, 0, irqvector);
            if (irqvector == 0xff) /* no IRQs pending */ {
                cpu_set_irq_line(1, 0, CLEAR_LINE);
            } else /* IRQ pending */ {
                cpu_set_irq_line(1, 0, ASSERT_LINE);
            }
        }
    };
    public static InitMachinePtr m72_init_sound = new InitMachinePtr() {
        public void handler() {
            setvector_callback.handler(VECTOR_INIT);
        }
    };
    public static WriteYmHandlerPtr m72_ym2151_irq_handler = new WriteYmHandlerPtr() {
        public void handler(int irq) {
            if (irq != 0) {
                timer_set(TIME_NOW, YM2151_ASSERT, setvector_callback);
            } else {
                timer_set(TIME_NOW, YM2151_CLEAR, setvector_callback);
            }
        }
    };

    public static WriteHandlerPtr m72_sound_command_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            if (offset == 0) {
                soundlatch_w.handler(offset, data);
                timer_set(TIME_NOW, Z80_ASSERT, setvector_callback);
            }
        }
    };

    public static WriteHandlerPtr m72_sound_irq_ack_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            timer_set(TIME_NOW, Z80_CLEAR, setvector_callback);
        }
    };

    static int sample_addr;

    public static void m72_set_sample_start(int start) {
        sample_addr = start;
    }

    public static WriteHandlerPtr vigilant_sample_addr_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            if (offset == 1) {
                sample_addr = (sample_addr & 0x00ff) | ((data << 8) & 0xff00);
            } else {
                sample_addr = (sample_addr & 0xff00) | ((data << 0) & 0x00ff);
            }
        }
    };

    public static WriteHandlerPtr shisen_sample_addr_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            sample_addr >>= 2;

            if (offset == 1) {
                sample_addr = (sample_addr & 0x00ff) | ((data << 8) & 0xff00);
            } else {
                sample_addr = (sample_addr & 0xff00) | ((data << 0) & 0x00ff);
            }

            sample_addr <<= 2;
        }
    };

    public static WriteHandlerPtr rtype2_sample_addr_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            sample_addr >>= 5;

            if (offset == 1) {
                sample_addr = (sample_addr & 0x00ff) | ((data << 8) & 0xff00);
            } else {
                sample_addr = (sample_addr & 0xff00) | ((data << 0) & 0x00ff);
            }

            sample_addr <<= 5;
        }
    };

    public static ReadHandlerPtr m72_sample_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            return memory_region(REGION_SOUND1).read(sample_addr);
        }
    };

    public static WriteHandlerPtr m72_sample_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            DAC_signed_data_w(0, data);
            sample_addr = (sample_addr + 1) & (memory_region_length(REGION_SOUND1) - 1);
        }
    };
}
