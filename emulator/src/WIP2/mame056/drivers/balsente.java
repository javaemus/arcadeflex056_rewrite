/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */
/**
 * Changelog
 * =========
 * 27/04/2019 Sound seems to work now (shadow)
 * 27/04/2019 Rewrote balsente  driver . Still have issues with cem3394 (shadow)
 */
package WIP2.mame056.drivers;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.ptr.*;
import static WIP2.common.libc.cstring.*;

import static WIP2.mame056.common.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.inptportH.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.driverH.*;
import static WIP2.mame056.memoryH.*;
import static WIP2.mame056.memory.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.inputH.*;
import static WIP2.mame056.sndintrfH.*;

import static WIP2.mame056.vidhrdw.generic.*;
import static WIP2.mame056.vidhrdw.balsente.*;

import static WIP2.arcadeflex056.fileio.*;
import static WIP2.common.libc.cstdlib.rand;
import static WIP2.mame056.cpu.m6809.m6809H.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.timer.*;
import static WIP2.mame056.timerH.*;
import static WIP2.common.libc.expressions.*;

// refactor
import static WIP2.arcadeflex036.osdepend.logerror;
import static WIP2.mame056.sound.cem3394.*;
import static WIP2.mame056.sound.cem3394H.*;
import static WIP2.mame056.sound.mixerH.*;
import static WIP2.mame056.sound.mixer.*;

public class balsente {

    /* global data */
    public static int/*UINT8*/ balsente_shooter;
    public static int/*UINT8*/ balsente_shooter_x;
    public static int/*UINT8*/ balsente_shooter_y;

    /* 8253 counter state */
    public static class counter_state {

        Object timer;
        int initial;
        int count;
        int/*UINT8*/ u8_gate;
        int/*UINT8*/ u8_out;
        int/*UINT8*/ u8_mode;
        int/*UINT8*/ u8_readbyte;
        int/*UINT8*/ u8_writebyte;

    }

    static counter_state[] counter = new counter_state[3];

    /* manually clocked counter 0 states */
    static int/*UINT8*/ u8_counter_control;
    static int/*UINT8*/ u8_counter_0_ff;
    static Object counter_0_timer;

    /* random number generator states */
    static UBytePtr poly17 = null;
    static UBytePtr rand17 = null;

    /* ADC I/O states */
    static byte[] analog_input_data = new byte[4];
    static int/*UINT8*/ u8_adc_value;
    static int/*UINT8*/ u8_adc_shift;

    /* CEM3394 DAC control states */
    static char dac_value;
    static int/*UINT8*/ u8_dac_register;
    static int/*UINT8*/ u8_chip_select;

    /* main CPU 6850 states */
    static int/*UINT8*/ u8_m6850_status;
    static int/*UINT8*/ u8_m6850_control;
    static int/*UINT8*/ u8_m6850_input;
    static int/*UINT8*/ u8_m6850_output;
    static int/*UINT8*/ u8_m6850_data_ready;

    /* sound CPU 6850 states */
    static int/*UINT8*/ u8_m6850_sound_status;
    static int/*UINT8*/ u8_m6850_sound_control;
    static int/*UINT8*/ u8_m6850_sound_input;
    static int/*UINT8*/ u8_m6850_sound_output;

    /* noise generator states */
    static int[]/*UINT32*/ noise_position = new int[6];

    /* game-specific states */
    static int/*UINT8*/ u8_nstocker_bits;
    static int/*UINT8*/ u8_spiker_expand_color;
    static int/*UINT8*/ u8_spiker_expand_bgcolor;
    static int/*UINT8*/ u8_spiker_expand_bits;

    static UBytePtr nvram = new UBytePtr();
    static int[] nvram_size = new int[1];

    static nvramPtr nvram_handler = new nvramPtr() {
        public void handler(Object file, int read_or_write) {
            if (read_or_write != 0) {
                osd_fwrite(file, nvram, nvram_size[0]);
            } else {
                if (file != null) {
                    osd_fread(file, nvram, nvram_size[0]);
                } else {
                    memset(nvram, 0, nvram_size[0]);
                }
            }
        }
    };

    /**
     * ***********************************
     *
     * Interrupt handling
     *
     ************************************
     */
    public static timer_callback irq_off = new timer_callback() {
        public void handler(int i) {
            cpu_set_irq_line(0, M6809_IRQ_LINE, CLEAR_LINE);
        }
    };

    public static timer_callback interrupt_timer = new timer_callback() {
        public void handler(int param) {
            /* next interrupt after scanline 256 is scanline 64 */
            if (param == 256) {
                timer_set(cpu_getscanlinetime(64), 64, interrupt_timer);
            } else {
                timer_set(cpu_getscanlinetime(param + 64), param + 64, interrupt_timer);
            }

            /* IRQ starts on scanline 0, 64, 128, etc. */
            cpu_set_irq_line(0, M6809_IRQ_LINE, ASSERT_LINE);

            /* it will turn off on the next HBLANK */
            timer_set(cpu_getscanlineperiod() * 0.9, 0, irq_off);

            /* if we're a shooter, we do a little more work */
            if (balsente_shooter != 0) {
                int/*UINT8*/ tempx, tempy;

                /* we latch the beam values on the first interrupt after VBLANK */
                if (param == 64 && balsente_shooter != 0) {
                    balsente_shooter_x = readinputport(8) & 0xFF;
                    balsente_shooter_y = readinputport(9) & 0xFF;
                }

                /* which bits get returned depends on which scanline we're at */
                tempx = (balsente_shooter_x << ((param - 64) / 64)) & 0xFF;
                tempy = (balsente_shooter_y << ((param - 64) / 64)) & 0xFF;
                u8_nstocker_bits = (((tempx >> 4) & 0x08) | ((tempx >> 1) & 0x04)
                        | ((tempy >> 6) & 0x02) | ((tempy >> 3) & 0x01)) & 0xFF;
            }
        }
    };

    public static InitMachinePtr init_machine = new InitMachinePtr() {
        public void handler() {
            /* create the polynomial tables */
            poly17_init();

            /* reset counters; counter 2's gate is tied high */
            for (int i = 0; i < 3; i++) {
                counter[i] = new counter_state();
            }
            counter[2].u8_gate = 1;

            /* reset the manual counter 0 clock */
            u8_counter_control = 0x00;
            u8_counter_0_ff = 0;
            counter_0_timer = null;

            /* reset the ADC states */
            u8_adc_value = 0;

            /* reset the CEM3394 I/O states */
            dac_value = 0;
            u8_dac_register = 0;
            u8_chip_select = 0x3f;

            /* reset the 6850 chips */
            m6850_w.handler(0, 3);
            m6850_sound_w.handler(0, 3);

            /* reset the noise generator */
            memset(noise_position, 0, sizeof(noise_position));

            /* point the banks to bank 0 */
            cpu_setbank(1, new UBytePtr(memory_region(REGION_CPU1), 0x10000));
            cpu_setbank(2, new UBytePtr(memory_region(REGION_CPU1), 0x12000));

            /* start a timer to generate interrupts */
            timer_set(cpu_getscanlinetime(0), 0, interrupt_timer);
        }
    };

    /**
     * ***********************************
     *
     * MM5837 noise generator
     *
     * NOTE: this is stolen straight from POKEY.c
     *
     ************************************
     */
    public static int POLY17_BITS = 17;
    public static int POLY17_SIZE = ((1 << POLY17_BITS) - 1);
    public static int POLY17_SHL = 7;
    public static int POLY17_SHR = 10;
    public static int POLY17_ADD = 0x18000;

    static void poly17_init() {
        long i, x = 0;
        UBytePtr p = new UBytePtr(), r = new UBytePtr();
        int _p = 0;
        int _r = 0;

        /* free stale memory */
        if (poly17 != null) {
            poly17 = rand17 = null;
        }

        /* allocate memory */
        p = poly17 = new UBytePtr(2 * (POLY17_SIZE + 1));
        if (poly17 == null) {
            return;
        }
        r = rand17 = new UBytePtr(poly17, POLY17_SIZE + 1);

        /* generate the polynomial */
        for (i = 0; i < POLY17_SIZE; i++) {
            /* store new values */
            p.write(_p++, (int) (x & 1));
            r.write(_r++, (int) (x >> 3));

            /* calculate next bit */
            x = ((x << POLY17_SHL) + (x >> POLY17_SHR) + POLY17_ADD) & POLY17_SIZE;
        }
    }

    public static externalPtr noise_gen = new externalPtr() {
        public void handler(int chip, int count, ShortPtr buffer) {
            if (Machine.sample_rate != 0) {
                /* noise generator runs at 100kHz */
 /*UINT32*/
                int step = (100000 << 14) / Machine.sample_rate;
                /*UINT32*/
                int noise_counter = noise_position[chip];

                /* try to use the poly17 if we can */
                if (poly17 != null) {
                    while ((count--) != 0) {
                        buffer.writeinc((short) (poly17.read((noise_counter >> 14) & POLY17_SIZE) << 12));
                        noise_counter += step;
                    }
                } /* otherwise just use random numbers */ else {
                    while ((count--) != 0) {
                        buffer.writeinc((short) (rand() & 0x1000));
                    }
                }

                /* remember the noise position */
                noise_position[chip] = noise_counter;
            }
        }
    };

    /**
     * ***********************************
     *
     * Hardware random numbers
     *
     ************************************
     */
    public static WriteHandlerPtr random_reset_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            /* reset random number generator */
        }
    };

    public static ReadHandlerPtr random_num_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            /*unsigned*/ int cc;

            /* CPU runs at 1.25MHz, noise source at 100kHz --> multiply by 12.5 */
            cc = cpu_gettotalcycles();

            /* 12.5 = 8 + 4 + 0.5 */
            cc = (cc << 3) + (cc << 2) + (cc >> 1);
            return rand17.read((cc & POLY17_SIZE));
        }
    };

    /**
     * ***********************************
     *
     * ROM banking
     *
     ************************************
     */
    public static WriteHandlerPtr rombank_select_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            int bank_offset = 0x6000 * ((data >> 4) & 7);

            /* the bank number comes from bits 4-6 */
            cpu_setbank(1, new UBytePtr(memory_region(REGION_CPU1), 0x10000 + bank_offset));
            cpu_setbank(2, new UBytePtr(memory_region(REGION_CPU1), 0x12000 + bank_offset));
        }
    };

    public static WriteHandlerPtr rombank2_select_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            /* Night Stocker and Name that Tune only so far.... */
            int bank = data & 7;

            /* top bit controls which half of the ROMs to use (Name that Tune only) */
            if (memory_region_length(REGION_CPU1) > 0x40000) {
                bank |= (data >> 4) & 8;
            }

            /* when they set the AB bank, it appears as though the CD bank is reset */
            if ((data & 0x20) != 0) {
                cpu_setbank(1, new UBytePtr(memory_region(REGION_CPU1), 0x10000 + 0x6000 * bank));
                cpu_setbank(2, new UBytePtr(memory_region(REGION_CPU1), 0x12000 + 0x6000 * 6));
            } /* set both banks */ else {
                cpu_setbank(1, new UBytePtr(memory_region(REGION_CPU1), 0x10000 + 0x6000 * bank));
                cpu_setbank(2, new UBytePtr(memory_region(REGION_CPU1), 0x12000 + 0x6000 * bank));
            }
        }
    };

    /**
     * ***********************************
     *
     * Special outputs
     *
     ************************************
     */
    public static WriteHandlerPtr misc_output_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            offset = (offset / 4) % 8;
            data >>= 7;

            /* these are generally used to control the various lamps */
 /* special case is offset 7, which recalls the NVRAM data */
            if (offset == 7) {
                logerror("nvrecall_w=%d\n", data);
            } else {
                //		set_led_status(offset, data);
            }
        }
    };

    /**
     * ***********************************
     *
     * 6850 UART communications
     *
     ************************************
     */
    static void m6850_update_io() {
        int/*UINT8*/ new_state;

        /* sound -> main CPU communications */
        if ((u8_m6850_sound_status & 0x02) == 0) {
            /* set the overrun bit if the data in the destination hasn't been read yet */
            if ((u8_m6850_status & 0x01) != 0) {
                u8_m6850_status |= 0x20;
            }

            /* copy the sound's output to our input */
            u8_m6850_input = u8_m6850_sound_output & 0xFF;

            /* set the receive register full bit */
            u8_m6850_status |= 0x01;

            /* set the sound's trasmitter register empty bit */
            u8_m6850_sound_status |= 0x02;
        }

        /* main -> sound CPU communications */
        if (u8_m6850_data_ready != 0) {
            /* set the overrun bit if the data in the destination hasn't been read yet */
            if ((u8_m6850_sound_status & 0x01) != 0) {
                u8_m6850_sound_status |= 0x20;
            }

            /* copy the main CPU's output to our input */
            u8_m6850_sound_input = u8_m6850_output & 0xFF;

            /* set the receive register full bit */
            u8_m6850_sound_status |= 0x01;

            /* set the main CPU's trasmitter register empty bit */
            u8_m6850_status |= 0x02;
            u8_m6850_data_ready = 0;
        }

        /* check for reset states */
        if ((u8_m6850_control & 3) == 3) {
            u8_m6850_status = 0x02;
            u8_m6850_data_ready = 0;
        }
        if ((u8_m6850_sound_control & 3) == 3) {
            u8_m6850_sound_status = 0x02;
        }

        /* check for transmit/receive IRQs on the main CPU */
        new_state = 0;
        if ((u8_m6850_control & 0x80) != 0 && (u8_m6850_status & 0x21) != 0) {
            new_state = 1;
        }
        if ((u8_m6850_control & 0x60) == 0x20 && (u8_m6850_status & 0x02) != 0) {
            new_state = 1;
        }

        /* apply the change */
        if (new_state != 0 && (u8_m6850_status & 0x80) == 0) {
            cpu_set_irq_line(0, M6809_FIRQ_LINE, ASSERT_LINE);
            u8_m6850_status |= 0x80;
        } else if (new_state == 0 && (u8_m6850_status & 0x80) != 0) {
            cpu_set_irq_line(0, M6809_FIRQ_LINE, CLEAR_LINE);
            u8_m6850_status = (u8_m6850_status & ~0x80) & 0xFF;
        }

        /* check for transmit/receive IRQs on the sound CPU */
        new_state = 0;
        if ((u8_m6850_sound_control & 0x80) != 0 && (u8_m6850_sound_status & 0x21) != 0) {
            new_state = 1;
        }
        if ((u8_m6850_sound_control & 0x60) == 0x20 && (u8_m6850_sound_status & 0x02) != 0) {
            new_state = 1;
        }
        if ((u8_counter_control & 0x20) == 0) {
            new_state = 0;
        }

        /* apply the change */
        if (new_state != 0 && (u8_m6850_sound_status & 0x80) == 0) {
            cpu_set_nmi_line(1, ASSERT_LINE);
            u8_m6850_sound_status |= 0x80;
        } else if (new_state == 0 && (u8_m6850_sound_status & 0x80) != 0) {
            cpu_set_nmi_line(1, CLEAR_LINE);
            u8_m6850_sound_status = (u8_m6850_sound_status & ~0x80) & 0xFF;
        }
    }

    /**
     * ***********************************
     *
     * 6850 UART (main CPU)
     *
     ************************************
     */
    public static ReadHandlerPtr m6850_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            int result;

            /* status register is at offset 0 */
            if (offset == 0) {
                result = u8_m6850_status & 0xFF;
            } /* input register is at offset 1 */ else {
                result = u8_m6850_input & 0xFF;

                /* clear the overrun and receive buffer full bits */
                u8_m6850_status = (u8_m6850_status & ~0x21) & 0xFF;
                m6850_update_io();
            }

            return result;
        }
    };

    public static timer_callback m6850_data_ready_callback = new timer_callback() {
        public void handler(int param) {
            /* set the output data byte and indicate that we're ready to go */
            u8_m6850_output = param & 0xFF;
            u8_m6850_data_ready = 1;
            m6850_update_io();
        }
    };

    public static timer_callback m6850_w_callback = new timer_callback() {
        public void handler(int param) {
            /* indicate that the transmit buffer is no longer empty and update the I/O state */
            u8_m6850_status = (u8_m6850_status & ~0x02) & 0xFF;
            m6850_update_io();

            /* set a timer for 500usec later to actually transmit the data */
 /* (this is very important for several games, esp Snacks'n Jaxson) */
            timer_set(TIME_IN_USEC(500), param, m6850_data_ready_callback);
        }
    };

    public static WriteHandlerPtr m6850_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            /* control register is at offset 0 */
            if (offset == 0) {
                u8_m6850_control = data & 0xFF;

                /* re-update since interrupt enables could have been modified */
                m6850_update_io();
            } /* output register is at offset 1; set a timer to synchronize the CPUs */ else {
                timer_set(TIME_NOW, data, m6850_w_callback);
            }
        }
    };

    /**
     * ***********************************
     *
     * 6850 UART (sound CPU)
     *
     ************************************
     */
    public static ReadHandlerPtr m6850_sound_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            int result;

            /* status register is at offset 0 */
            if (offset == 0) {
                result = u8_m6850_sound_status & 0xFF;
            } /* input register is at offset 1 */ else {
                result = u8_m6850_sound_input & 0xFF;

                /* clear the overrun and receive buffer full bits */
                u8_m6850_sound_status = (u8_m6850_sound_status & ~0x21) & 0xFF;
                m6850_update_io();
            }

            return result;
        }
    };

    public static WriteHandlerPtr m6850_sound_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            /* control register is at offset 0 */
            if (offset == 0) {
                u8_m6850_sound_control = data & 0xFF;
            } /* output register is at offset 1 */ else {
                u8_m6850_sound_output = data & 0xFF;
                u8_m6850_sound_status = (u8_m6850_sound_status & ~0x02) & 0xFF;
            }

            /* re-update since interrupt enables could have been modified */
            m6850_update_io();
        }
    };

    /**
     * ***********************************
     *
     * ADC handlers
     *
     ************************************
     */
    public static InterruptPtr update_analog_inputs = new InterruptPtr() {
        public int handler() {
            int i;

            /* the analog input system helpfully scales the value read by the percentage of time */
 /* into the current frame we are; unfortunately, this is bad for us, since the analog */
 /* ports are read once a frame, just at varying intervals. To get around this, we */
 /* read all the analog inputs at VBLANK time and just return the cached values. */
            for (i = 0; i < 4; i++) {
                analog_input_data[i] = (byte) readinputport(4 + i);
            }

            /* don't actually generate an interrupt here */
            return ignore_interrupt.handler();
        }
    };

    public static timer_callback adc_finished = new timer_callback() {
        public void handler(int which) {
            /* analog controls are read in two pieces; the lower port returns the sign */
 /* and the upper port returns the absolute value of the magnitude */
            int val = analog_input_data[which / 2] << u8_adc_shift;

            /* special case for Stompin' */
            if (u8_adc_shift == 32) {
                u8_adc_value = (((char) analog_input_data[which]) & 0xFF);
                return;
            }

            /* push everything out a little bit extra; most games seem to have a dead */
 /* zone in the middle that feels unnatural with the mouse */
            if (val < 0) {
                val -= 8;
            } else if (val > 0) {
                val += 8;
            }

            /* clip to 0xff maximum magnitude */
            if (val < -0xff) {
                val = -0xff;
            } else if (val > 0xff) {
                val = 0xff;
            }

            /* return the sign */
            if ((which & 1) == 0) {
                u8_adc_value = (val < 0) ? 0xff : 0x00;
            } /* return the magnitude */ else {
                u8_adc_value = (val < 0) ? -val : val;
            }
        }
    };

    public static ReadHandlerPtr adc_data_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            /* just return the last value read */
            return u8_adc_value & 0xFF;
        }
    };

    public static WriteHandlerPtr adc_select_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            /* set a timer to go off and read the value after 50us */
 /* it's important that we do this for Mini Golf */
            timer_set(TIME_IN_USEC(50), offset & 7, adc_finished);
        }
    };

    /**
     * ***********************************
     *
     * 8253-5 timer utilities
     *
     * NOTE: this is far from complete!
     *
     ************************************
     */
    public static void counter_start(int which) {
        /* don't start a timer for channel 0; it is clocked manually */
        if (which != 0) {
            /* only start a timer if we're gated and there is none already */
            if (counter[which].u8_gate != 0 && counter[which].timer == null) {
                counter[which].timer = timer_set(TIME_IN_HZ(2000000) * (double) counter[which].count, which, counter_callback);
            }
        }
    }

    public static void counter_stop(int which) {
        /* only stop the timer if it exists */
        if (counter[which].timer != null) {
            timer_remove(counter[which].timer);
        }
        counter[which].timer = null;
    }

    public static void counter_update_count(int which) {
        /* only update if the timer is running */
        if (counter[which].timer != null) {
            /* determine how many 2MHz cycles are remaining */
            int count = (int) (timer_timeleft(counter[which].timer) / TIME_IN_HZ(2000000));
            counter[which].count = (count < 0) ? 0 : count;
        }
    }

    /**
     * ***********************************
     *
     * 8253-5 timer internals
     *
     * NOTE: this is far from complete!
     *
     ************************************
     */
    static void counter_set_gate(int which, int gate) {
        int oldgate = counter[which].u8_gate;

        /* remember the gate state */
        counter[which].u8_gate = gate & 0xFF;

        /* if the counter is being halted, update the count and remove the system timer */
        if (gate == 0 && oldgate != 0) {
            counter_update_count(which);
            counter_stop(which);
        } /* if the counter is being started, create the timer */ else if (gate != 0 && oldgate == 0) {
            /* mode 1 waits for the gate to trigger the counter */
            if (counter[which].u8_mode == 1) {
                counter_set_out(which, 0);

                /* add one to the count; technically, OUT goes low on the next clock pulse */
 /* and then starts counting down; it's important that we don't count the first one */
                counter[which].count = counter[which].initial + 1;
            }

            /* start the counter */
            counter_start(which);
        }
    }

    static void counter_set_out(int which, int out) {
        /* OUT on counter 2 is hooked to the /INT line on the Z80 */
        if (which == 2) {
            cpu_set_irq_line(1, 0, out != 0 ? ASSERT_LINE : CLEAR_LINE);
        } /* OUT on counter 0 is hooked to the GATE line on counter 1 */ else if (which == 0) {
            counter_set_gate(1, NOT(out));
        }

        /* remember the out state */
        counter[which].u8_out = out & 0xFF;
    }

    public static timer_callback counter_callback = new timer_callback() {
        public void handler(int param) {
            /* reset the counter and the count */
            counter[param].timer = null;
            counter[param].count = 0;

            /* set the state of the OUT line */
 /* mode 0 and 1: when firing, transition OUT to high */
            if (counter[param].u8_mode == 0 || counter[param].u8_mode == 1) {
                counter_set_out(param, 1);
            }

            /* no other modes handled currently */
        }
    };

    /**
     * ***********************************
     *
     * 8253-5 timer handlers
     *
     * NOTE: this is far from complete!
     *
     ************************************
     */
    public static ReadHandlerPtr counter_8253_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            int which;

            switch (offset & 3) {
                case 0:
                case 1:
                case 2:
                    /* warning: assumes LSB/MSB addressing and no latching! */
                    which = offset & 3;

                    /* update the count */
                    counter_update_count(which);

                    /* return the LSB */
                    if (counter[which].u8_readbyte == 0) {
                        counter[which].u8_readbyte = 1;
                        return counter[which].count & 0xff;
                    } /* write the MSB and reset the counter */ else {
                        counter[which].u8_readbyte = 0;
                        return (counter[which].count >> 8) & 0xff;
                    }
                //break;
            }
            return 0;
        }
    };

    public static WriteHandlerPtr counter_8253_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            int which;

            switch (offset & 3) {
                case 0:
                case 1:
                case 2:
                    /* warning: assumes LSB/MSB addressing and no latching! */
                    which = offset & 3;

                    /* if the counter is in mode 0, a write here will reset the OUT state */
                    if (counter[which].u8_mode == 0) {
                        counter_set_out(which, 0);
                    }

                    /* write the LSB */
                    if (counter[which].u8_writebyte == 0) {
                        counter[which].count = (counter[which].count & 0xff00) | (data & 0x00ff);
                        counter[which].initial = (counter[which].initial & 0xff00) | (data & 0x00ff);
                        counter[which].u8_writebyte = 1;
                    } /* write the MSB and reset the counter */ else {
                        counter[which].count = (counter[which].count & 0x00ff) | ((data << 8) & 0xff00);
                        counter[which].initial = (counter[which].initial & 0x00ff) | ((data << 8) & 0xff00);
                        counter[which].u8_writebyte = 0;

                        /* treat 0 as $10000 */
                        if (counter[which].count == 0) {
                            counter[which].count = counter[which].initial = 0x10000;
                        }

                        /* remove any old timer and set a new one */
                        counter_stop(which);

                        /* note that in mode 1, we have to wait for a rising edge of a gate */
                        if (counter[which].u8_mode == 0) {
                            counter_start(which);
                        }

                        /* if the counter is in mode 1, a write here will set the OUT state */
                        if (counter[which].u8_mode == 1) {
                            counter_set_out(which, 1);
                        }
                    }
                    break;

                case 3:
                    /* determine which counter */
                    which = data >> 6;
                    if (which == 3) {
                        break;
                    }

                    /* if the counter was in mode 0, a write here will reset the OUT state */
                    if (((counter[which].u8_mode >> 1) & 7) == 0) {
                        counter_set_out(which, 0);
                    }

                    /* set the mode */
                    counter[which].u8_mode = (data >> 1) & 7;

                    /* if the counter is in mode 0, a write here will reset the OUT state */
                    if (counter[which].u8_mode == 0) {
                        counter_set_out(which, 0);
                    }
                    break;
            }
        }
    };

    /**
     * ***********************************
     *
     * Sound CPU counter 0 emulation
     *
     ************************************
     */
    static void set_counter_0_ff(int newstate) {
        /* the flip/flop output is inverted, so if we went high to low, that's a clock */
        if (u8_counter_0_ff != 0 && newstate == 0) {
            /* only count if gated and non-zero */
            if (counter[0].count > 0 && counter[0].u8_gate != 0) {
                counter[0].count--;
                if (counter[0].count == 0) {
                    counter_callback.handler(0);
                }
            }
        }

        /* remember the new state */
        u8_counter_0_ff = newstate & 0xFF;
    }

    public static timer_callback clock_counter_0_ff = new timer_callback() {
        public void handler(int param) {
            /* clock the D value through the flip-flop */
            set_counter_0_ff((u8_counter_control >> 3) & 1);
        }
    };

    static void update_counter_0_timer() {
        double maxfreq = 0.0;
        int i;

        /* if there's already a timer, remove it */
        if (counter_0_timer != null) {
            timer_remove(counter_0_timer);
        }
        counter_0_timer = null;

        /* find the counter with the maximum frequency */
 /* this is used to calibrate the timers at startup */
        for (i = 0; i < 6; i++) {
            if (cem3394_get_parameter(i, CEM3394_FINAL_GAIN) < 10.0) {
                double tempfreq;

                /* if the filter resonance is high, then they're calibrating the filter frequency */
                if (cem3394_get_parameter(i, CEM3394_FILTER_RESONANCE) > 0.9) {
                    tempfreq = cem3394_get_parameter(i, CEM3394_FILTER_FREQENCY);
                } /* otherwise, they're calibrating the VCO frequency */ else {
                    tempfreq = cem3394_get_parameter(i, CEM3394_VCO_FREQUENCY);
                }

                if (tempfreq > maxfreq) {
                    maxfreq = tempfreq;
                }
            }
        }

        /* reprime the timer */
        if (maxfreq > 0.0) {
            counter_0_timer = timer_pulse(TIME_IN_HZ(maxfreq), 0, clock_counter_0_ff);
        }
    }

    /**
     * ***********************************
     *
     * Sound CPU counter handlers
     *
     ************************************
     */
    public static ReadHandlerPtr counter_state_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            /* bit D0 is the inverse of the flip-flop state */
            int result = NOT(u8_counter_0_ff);

            /* bit D1 is the OUT value from counter 0 */
            if (counter[0].u8_out != 0) {
                result |= 0x02;
            }

            return result;
        }
    };

    public static WriteHandlerPtr counter_control_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            int/*UINT8*/ u8_diff_counter_control = (u8_counter_control ^ data) & 0xFF;

            /* set the new global value */
            u8_counter_control = data & 0xFF;

            /* bit D0 enables/disables audio */
            if ((u8_diff_counter_control & 0x01) != 0) {
                int ch;
                for (ch = 0; ch < MIXER_MAX_CHANNELS; ch++) {
                    String name = mixer_get_name(ch);
                    if (name != null && strstr(name, "3394") != 0) {
                        mixer_set_volume(ch, (data & 0x01) != 0 ? 100 : 0);
                    }
                }
            }

            /* bit D1 is hooked to counter 0's gate */
 /* if we gate on, start a pulsing timer to clock it */
            if (counter[0].u8_gate == 0 && (data & 0x02) != 0 && counter_0_timer == null) {
                update_counter_0_timer();
            } /* if we gate off, remove the timer */ else if (counter[0].u8_gate != 0 && (data & 0x02) == 0 && counter_0_timer != null) {
                timer_remove(counter_0_timer);
                counter_0_timer = null;
            }

            /* set the actual gate afterwards, since we need to know the old value above */
            counter_set_gate(0, (data >> 1) & 1);

            /* bits D2 and D4 control the clear/reset flags on the flip-flop that feeds counter 0 */
            if ((data & 0x04) == 0) {
                set_counter_0_ff(1);
            }
            if ((data & 0x10) == 0) {
                set_counter_0_ff(0);
            }

            /* bit 5 clears the NMI interrupt; recompute the I/O state now */
            m6850_update_io();
        }
    };

    /**
     * ***********************************
     *
     * Game-specific handlers
     *
     ************************************
     */
    public static ReadHandlerPtr nstocker_port2_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            return (readinputport(2) & 0xf0) | u8_nstocker_bits;
        }
    };

    public static WriteHandlerPtr spiker_expand_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            /* offset 0 is the bit pattern */
            if (offset == 0) {
                u8_spiker_expand_bits = data & 0xFF;
            } /* offset 1 is the background color (cleared on each read) */ else if (offset == 1) {
                u8_spiker_expand_bgcolor = data & 0xFF;
            } /* offset 2 is the color */ else if (offset == 2) {
                u8_spiker_expand_color = data & 0xFF;
            }
        }
    };

    public static ReadHandlerPtr spiker_expand_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            int/*UINT8*/ left, right;

            /* first rotate each nibble */
            u8_spiker_expand_bits = (((u8_spiker_expand_bits << 1) & 0xee) | ((u8_spiker_expand_bits >> 3) & 0x11)) & 0xFF;

            /* compute left and right pixels */
            left = (u8_spiker_expand_bits & 0x10) != 0 ? u8_spiker_expand_color : u8_spiker_expand_bgcolor;
            right = (u8_spiker_expand_bits & 0x01) != 0 ? u8_spiker_expand_color : u8_spiker_expand_bgcolor;

            /* reset the background color */
            u8_spiker_expand_bgcolor = 0;

            /* return the combined result */
            return (left & 0xf0) | (right & 0x0f);
        }
    };

    /**
     * ***********************************
     *
     * CEM3394 Interfaces
     *
     ************************************
     */
    public static WriteHandlerPtr chip_select_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            int register_map[]
                    = {
                        CEM3394_VCO_FREQUENCY,
                        CEM3394_FINAL_GAIN,
                        CEM3394_FILTER_RESONANCE,
                        CEM3394_FILTER_FREQENCY,
                        CEM3394_MIXER_BALANCE,
                        CEM3394_MODULATION_AMOUNT,
                        CEM3394_PULSE_WIDTH,
                        CEM3394_WAVE_SELECT
                    };

            double voltage = (double) dac_value * (8.0 / 4096.0) - 4.0;
            int diffchip = data ^ u8_chip_select, i;
            int reg = register_map[u8_dac_register];

            /* remember the new select value */
            u8_chip_select = data & 0xFF;

            /* check all six chip enables */
            for (i = 0; i < 6; i++) {
                if ((diffchip & (1 << i)) != 0 && (data & (1 << i)) != 0) {
                    double temp = 0;

                    /* remember the previous value */
                    temp = cem3394_get_parameter(i, reg);

                    /* set the voltage */
                    cem3394_set_voltage(i, reg, voltage);
                }
            }

            /* if a timer for counter 0 is running, recompute */
            if (counter_0_timer != null) {
                update_counter_0_timer();
            }
        }
    };

    public static WriteHandlerPtr dac_data_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            /* LSB or MSB? */
            if ((offset & 1) != 0) {
                dac_value = (char) ((dac_value & 0xfc0) | ((data >> 2) & 0x03f));
            } else {
                dac_value = (char) ((dac_value & 0x03f) | ((data << 6) & 0xfc0));
            }

            /* if there are open channels, force the values in */
            if ((u8_chip_select & 0x3f) != 0x3f) {
                int/*UINT8*/ temp = u8_chip_select & 0xFF;
                chip_select_w.handler(0, 0x3f);
                chip_select_w.handler(0, temp);
            }
        }
    };

    public static WriteHandlerPtr register_addr_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            u8_dac_register = data & 7;
        }
    };

    /**
     * ***********************************
     *
     * Main CPU memory handlers
     *
     ************************************
     */
    /* CPU 1 read addresses */
    public static Memory_ReadAddress readmem_cpu1[] = {
        new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
        new Memory_ReadAddress(0x0000, 0x8fff, MRA_RAM),
        new Memory_ReadAddress(0x9400, 0x9400, adc_data_r),
        new Memory_ReadAddress(0x9900, 0x9900, input_port_0_r),
        new Memory_ReadAddress(0x9901, 0x9901, input_port_1_r),
        new Memory_ReadAddress(0x9902, 0x9902, input_port_2_r),
        new Memory_ReadAddress(0x9903, 0x9903, input_port_3_r),
        new Memory_ReadAddress(0x9a00, 0x9a03, random_num_r),
        new Memory_ReadAddress(0x9a04, 0x9a05, m6850_r),
        new Memory_ReadAddress(0x9b00, 0x9bff, MRA_RAM), /* system NOVRAM */
        new Memory_ReadAddress(0x9c00, 0x9cff, MRA_RAM), /* cart NOVRAM */
        new Memory_ReadAddress(0xa000, 0xbfff, MRA_BANK1),
        new Memory_ReadAddress(0xc000, 0xffff, MRA_BANK2),
        new Memory_ReadAddress(MEMPORT_MARKER, 0)
    };

    /* CPU 1 write addresses */
    public static Memory_WriteAddress writemem_cpu1[] = {
        new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
        new Memory_WriteAddress(0x0000, 0x07ff, MWA_RAM, spriteram),
        new Memory_WriteAddress(0x0800, 0x7fff, balsente_videoram_w, videoram, videoram_size),
        new Memory_WriteAddress(0x8000, 0x8fff, balsente_paletteram_w, paletteram),
        new Memory_WriteAddress(0x9000, 0x9007, adc_select_w),
        new Memory_WriteAddress(0x9800, 0x987f, misc_output_w),
        new Memory_WriteAddress(0x9880, 0x989f, random_reset_w),
        new Memory_WriteAddress(0x98a0, 0x98bf, rombank_select_w),
        new Memory_WriteAddress(0x98c0, 0x98df, balsente_palette_select_w),
        new Memory_WriteAddress(0x98e0, 0x98ff, watchdog_reset_w),
        new Memory_WriteAddress(0x9903, 0x9903, MWA_NOP),
        new Memory_WriteAddress(0x9a04, 0x9a05, m6850_w),
        new Memory_WriteAddress(0x9b00, 0x9cff, MWA_RAM, nvram, nvram_size), /* system NOVRAM + cart NOVRAM */
        new Memory_WriteAddress(0x9f00, 0x9f00, rombank2_select_w),
        new Memory_WriteAddress(0xa000, 0xffff, MWA_ROM),
        new Memory_WriteAddress(MEMPORT_MARKER, 0)
    };

    /**
     * ***********************************
     *
     * Sound CPU memory handlers
     *
     ************************************
     */
    public static Memory_ReadAddress readmem_cpu2[] = {
        new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
        new Memory_ReadAddress(0x0000, 0x1fff, MRA_ROM),
        new Memory_ReadAddress(0x2000, 0x5fff, MRA_RAM),
        new Memory_ReadAddress(0xe000, 0xffff, m6850_sound_r),
        new Memory_ReadAddress(MEMPORT_MARKER, 0)
    };

    public static Memory_WriteAddress writemem_cpu2[] = {
        new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
        new Memory_WriteAddress(0x0000, 0x1fff, MWA_ROM),
        new Memory_WriteAddress(0x2000, 0x5fff, MWA_RAM),
        new Memory_WriteAddress(0x6000, 0x7fff, m6850_sound_w),
        new Memory_WriteAddress(MEMPORT_MARKER, 0)
    };

    public static IO_ReadPort readport_cpu2[] = {
        new IO_ReadPort(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),
        new IO_ReadPort(0x00, 0x03, counter_8253_r),
        new IO_ReadPort(0x08, 0x0f, counter_state_r),
        new IO_ReadPort(MEMPORT_MARKER, 0)
    };

    public static IO_WritePort writeport_cpu2[] = {
        new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),
        new IO_WritePort(0x00, 0x03, counter_8253_w),
        new IO_WritePort(0x08, 0x09, counter_control_w),
        new IO_WritePort(0x0a, 0x0b, dac_data_w),
        new IO_WritePort(0x0c, 0x0d, register_addr_w),
        new IO_WritePort(0x0e, 0x0f, chip_select_w),
        new IO_WritePort(MEMPORT_MARKER, 0)
    };

    /**
     * ***********************************
     *
     * Port definitions
     *
     ************************************
     */
    public static void UNUSED_ANALOG() {
        PORT_START();
        PORT_BIT(0xff, IP_ACTIVE_HIGH, IPT_UNUSED);
    }

    public static void UNUSED_ANALOG_X2() {
        UNUSED_ANALOG();
        UNUSED_ANALOG();
    }

    public static void UNUSED_ANALOG_X3() {
        UNUSED_ANALOG_X2();
        UNUSED_ANALOG();
    }

    public static void UNUSED_ANALOG_X4() {
        UNUSED_ANALOG_X2();
        UNUSED_ANALOG_X2();
    }

    public static void PLAYER1_ANALOG_JOYSTICK() {
        PORT_START();
        PORT_ANALOG(0xff, 0, IPT_AD_STICK_Y | IPF_PLAYER1, 100, 20, 0x80, 0x7f);
        PORT_START();
        PORT_ANALOG(0xff, 0, IPT_AD_STICK_X | IPF_REVERSE | IPF_PLAYER1, 100, 20, 0x80, 0x7f);
    }

    public static void PLAYER2_ANALOG_JOYSTICK() {
        PORT_START();
        PORT_ANALOG(0xff, 0, IPT_AD_STICK_Y | IPF_PLAYER2, 100, 20, 0x80, 0x7f);
        PORT_START();
        PORT_ANALOG(0xff, 0, IPT_AD_STICK_X | IPF_REVERSE | IPF_PLAYER2, 100, 20, 0x80, 0x7f);
    }

    public static void PLAYER1_TRACKBALL() {
        PORT_START();
        PORT_ANALOG(0xff, 0, IPT_TRACKBALL_Y | IPF_PLAYER1 | IPF_CENTER, 100, 20, 0, 0);
        PORT_START();
        PORT_ANALOG(0xff, 0, IPT_TRACKBALL_X | IPF_REVERSE | IPF_PLAYER1 | IPF_CENTER, 100, 20, 0, 0);
    }

    public static void PLAYER2_TRACKBALL() {
        PORT_START();
        PORT_ANALOG(0xff, 0, IPT_TRACKBALL_Y | IPF_PLAYER2 | IPF_CENTER, 100, 20, 0, 0);
        PORT_START();
        PORT_ANALOG(0xff, 0, IPT_TRACKBALL_X | IPF_REVERSE | IPF_PLAYER2 | IPF_CENTER, 100, 20, 0, 0);
    }

    public static void PLAYER1_DIAL() {
        PORT_START();
        PORT_ANALOG(0xff, 0, IPT_DIAL | IPF_PLAYER1 | IPF_REVERSE | IPF_CENTER, 100, 20, 0, 0);
    }

    public static void PLAYER2_DIAL() {
        PORT_START();
        PORT_ANALOG(0xff, 0, IPT_DIAL | IPF_PLAYER2 | IPF_REVERSE | IPF_CENTER, 100, 20, 0, 0);
    }

    public static void PLAYER1_WHEEL() {
        PORT_START();
        PORT_ANALOG(0xff, 0, IPT_DIAL | IPF_PLAYER1 | IPF_CENTER, 100, 20, 0, 0);
    }

    public static void PLAYER1_CROSSHAIRS() {
        PORT_START();
        /* fake analog X */
        PORT_ANALOG(0xff, 0x80, IPT_AD_STICK_X, 50, 10, 0, 255);
        PORT_START();
        /* fake analog Y */
        PORT_ANALOG(0xff, 0x80, IPT_AD_STICK_Y, 70, 10, 0, 255);
    }

    static InputPortPtr input_ports_sentetst = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x03, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x03, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPSETTING(0x00, DEF_STR("Free_Play"));
            PORT_BIT(0x7c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x80, "High Scores");
            PORT_DIPSETTING(0x80, "Keep Top 5");
            PORT_DIPSETTING(0x00, "Keep All");

            PORT_START();
            /* IN1 */
            PORT_DIPNAME(0x03, 0x00, DEF_STR("Bonus_Life"));
            PORT_DIPSETTING(0x00, "Every 10,000");
            PORT_DIPSETTING(0x01, "Every 15,000");
            PORT_DIPSETTING(0x02, "Every 20,000");
            PORT_DIPSETTING(0x03, "Every 25,000");
            PORT_DIPNAME(0x0c, 0x04, DEF_STR("Lives"));
            PORT_DIPSETTING(0x00, "2");
            PORT_DIPSETTING(0x04, "3");
            PORT_DIPSETTING(0x08, "4");
            PORT_DIPSETTING(0x0c, "5");
            PORT_BIT(0x30, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x40, 0x40, DEF_STR("Difficulty"));
            PORT_DIPSETTING(0x40, "Easy");
            PORT_DIPSETTING(0x00, "Hard");
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_UP | IPF_PLAYER1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_DOWN | IPF_PLAYER1);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_RIGHT | IPF_PLAYER1);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_LEFT | IPF_PLAYER1);
            PORT_BIT(0x30, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x38, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X2();
            PLAYER1_TRACKBALL();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_cshift = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x03, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x03, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPSETTING(0x00, DEF_STR("Free_Play"));
            PORT_BIT(0x7c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x80, "High Scores");
            PORT_DIPSETTING(0x80, "Keep Top 5");
            PORT_DIPSETTING(0x00, "Keep All");

            PORT_START();
            /* IN1 */
            PORT_BIT(0x03, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x04, 0x04, DEF_STR("Lives"));
            PORT_DIPSETTING(0x00, "2");
            PORT_DIPSETTING(0x04, "3");
            PORT_BIT(0x70, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_BUTTON2);
            PORT_BIT(0x3c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x3c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X4();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_gghost = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x03, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x03, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPSETTING(0x00, DEF_STR("Free_Play"));
            PORT_DIPNAME(0x04, 0x04, "Players per Credit");
            PORT_DIPSETTING(0x00, "1");
            PORT_DIPSETTING(0x04, "1 or 2");
            PORT_BIT(0xf8, IP_ACTIVE_HIGH, IPT_UNUSED);

            PORT_START();
            /* IN1 */
            PORT_DIPNAME(0x07, 0x05, "Game Duration");
            PORT_DIPSETTING(0x00, "9 points");
            PORT_DIPSETTING(0x02, "11 points");
            PORT_DIPSETTING(0x04, "15 points");
            PORT_DIPSETTING(0x06, "21 points");
            PORT_DIPSETTING(0x01, "timed, 1:15");
            PORT_DIPSETTING(0x03, "timed, 1:30");
            PORT_DIPSETTING(0x05, "timed, 2:00");
            PORT_DIPSETTING(0x07, "timed, 2:30");
            PORT_BIT(0x78, IP_ACTIVE_HIGH, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x0f, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER2);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER1);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER1);
            PORT_BIT(0x30, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            PLAYER2_TRACKBALL();
            PLAYER1_TRACKBALL();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_hattrick = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x03, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x03, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPSETTING(0x00, DEF_STR("Free_Play"));
            PORT_DIPNAME(0x04, 0x04, "Players Per Credit");
            PORT_DIPSETTING(0x00, "1");
            PORT_DIPSETTING(0x04, "1 or 2");
            PORT_BIT(0xf8, IP_ACTIVE_HIGH, IPT_UNUSED);

            PORT_START();
            /* IN1 */
            PORT_DIPNAME(0x07, 0x02, "Game Time");
            PORT_DIPSETTING(0x00, "1:15");
            PORT_DIPSETTING(0x01, "1:30");
            PORT_DIPSETTING(0x02, "1:45");
            PORT_DIPSETTING(0x03, "2:00");
            PORT_DIPSETTING(0x04, "2:15");
            PORT_DIPSETTING(0x05, "2:30");
            PORT_DIPSETTING(0x06, "2:45");
            PORT_DIPSETTING(0x07, "3:00");
            PORT_BIT(0x78, IP_ACTIVE_HIGH, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_PLAYER1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_PLAYER1);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_PLAYER1);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_PLAYER1);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_PLAYER2);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_PLAYER2);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_PLAYER2);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_PLAYER2);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X4();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_otwalls = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x03, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x03, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPSETTING(0x00, DEF_STR("Free_Play"));
            PORT_DIPNAME(0x04, 0x04, "Players Per Credit");
            PORT_DIPSETTING(0x00, "1");
            PORT_DIPSETTING(0x04, "1 or 2");
            PORT_BIT(0xf8, IP_ACTIVE_LOW, IPT_UNUSED);

            PORT_START();
            /* IN1 */
            PORT_BIT(0x7f, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_LEFT | IPF_PLAYER2);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_RIGHT | IPF_PLAYER2);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_LEFT | IPF_PLAYER1);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_RIGHT | IPF_PLAYER1);
            PORT_BIT(0x30, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x3c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X2();
            PLAYER1_DIAL();
            PLAYER2_DIAL();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_snakepit = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x03, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x03, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPSETTING(0x00, DEF_STR("Free_Play"));
            PORT_BIT(0x7c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x80, "High Scores");
            PORT_DIPSETTING(0x80, "Keep Top 5");
            PORT_DIPSETTING(0x00, "Keep All");

            PORT_START();
            /* IN1 */
            PORT_DIPNAME(0x03, 0x00, DEF_STR("Bonus_Life"));
            PORT_DIPSETTING(0x00, "Every 10,000");
            PORT_DIPSETTING(0x01, "Every 15,000");
            PORT_DIPSETTING(0x02, "Every 20,000");
            PORT_DIPSETTING(0x03, "Every 25,000");
            PORT_DIPNAME(0x0c, 0x04, DEF_STR("Lives"));
            PORT_DIPSETTING(0x00, "2");
            PORT_DIPSETTING(0x04, "3");
            PORT_DIPSETTING(0x08, "4");
            PORT_DIPSETTING(0x0c, "5");
            PORT_BIT(0x30, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x40, 0x40, DEF_STR("Difficulty"));
            PORT_DIPSETTING(0x40, "Easy");
            PORT_DIPSETTING(0x00, "Hard");
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_UP | IPF_PLAYER1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_DOWN | IPF_PLAYER1);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_RIGHT | IPF_PLAYER1);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_LEFT | IPF_PLAYER1);
            PORT_BIT(0x30, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x38, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X2();
            PLAYER1_TRACKBALL();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_snakjack = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x03, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x03, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPSETTING(0x00, DEF_STR("Free_Play"));
            PORT_BIT(0x7c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x80, "High Scores");
            PORT_DIPSETTING(0x80, "Keep Top 5");
            PORT_DIPSETTING(0x00, "Keep All");

            PORT_START();
            /* IN1 */
            PORT_DIPNAME(0x03, 0x01, DEF_STR("Bonus_Life"));
            PORT_DIPSETTING(0x00, "Every 15,000");
            PORT_DIPSETTING(0x01, "Every 20,000");
            PORT_DIPSETTING(0x02, "Every 25,000");
            PORT_DIPSETTING(0x03, "Every 30,000");
            PORT_DIPNAME(0x0c, 0x04, DEF_STR("Lives"));
            PORT_DIPSETTING(0x00, "2");
            PORT_DIPSETTING(0x04, "3");
            PORT_DIPSETTING(0x08, "4");
            PORT_DIPSETTING(0x0c, "5");
            PORT_BIT(0x70, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x3f, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x38, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X2();
            PLAYER1_TRACKBALL();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_stocker = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x01, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x03, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x02, DEF_STR("1C_2C"));
            PORT_DIPSETTING(0x00, DEF_STR("Free_Play"));
            PORT_DIPNAME(0x1c, 0x00, "Bonus Coins");
            PORT_DIPSETTING(0x00, "None");
            PORT_DIPSETTING(0x04, "2 Coins = 1 Bonus");
            PORT_DIPSETTING(0x08, "3 Coins = 1 Bonus");
            PORT_DIPSETTING(0x0c, "4 Coins = 1 Bonus");
            PORT_DIPSETTING(0x10, "4 Coins = 2 Bonus");
            PORT_DIPSETTING(0x14, "5 Coins = 1 Bonus");
            PORT_DIPSETTING(0x18, "5 Coins = 2 Bonus");
            PORT_DIPSETTING(0x1c, "5 Coins = 3 Bonus");
            PORT_DIPNAME(0x20, 0x00, "Left Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x20, "x2");
            PORT_DIPNAME(0xc0, 0x00, "Right Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x40, "x4");
            PORT_DIPSETTING(0x80, "x5");
            PORT_DIPSETTING(0xc0, "x6");

            PORT_START();
            /* IN1 */
            PORT_BIT(0x3f, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x40, 0x40, "End of Game");
            PORT_DIPSETTING(0x40, "Normal");
            PORT_DIPSETTING(0x00, "3 Tickets");
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x3f, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x3c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X3();
            PLAYER1_WHEEL();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_triviag1 = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x03, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x03, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPSETTING(0x00, DEF_STR("Free_Play"));
            PORT_BIT(0x1c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x20, 0x00, "Sound");
            PORT_DIPSETTING(0x20, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x40, 0x00, "Sound Test");
            PORT_DIPSETTING(0x40, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x80, 0x80, "High Scores");
            PORT_DIPSETTING(0x00, "Keep Top 5");
            PORT_DIPSETTING(0x80, "Keep Top 10");

            PORT_START();
            /* IN1 */
            PORT_BIT(0x03, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x0c, 0x04, "Guesses");
            PORT_DIPSETTING(0x00, "3");
            PORT_DIPSETTING(0x04, "4");
            PORT_DIPSETTING(0x08, "5");
            PORT_DIPSETTING(0x0c, "6");
            PORT_BIT(0x70, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_PLAYER1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_PLAYER1);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_PLAYER1);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_PLAYER1);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_START3);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_START4);
            PORT_BITX(0x10, IP_ACTIVE_LOW, IPT_BUTTON2, "Red Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BITX(0x20, IP_ACTIVE_LOW, IPT_BUTTON1, "Green Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X4();
            INPUT_PORTS_END();
        }
    };
    
    static InputPortPtr input_ports_triviaes = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME( 0x03, 0x00, DEF_STR( "Coinage" ) );
            PORT_DIPSETTING(    0x02, DEF_STR( "2C_1C" ) );
            PORT_DIPSETTING(    0x03, DEF_STR( "Free_Play" ) );
            PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
            PORT_DIPSETTING(    0x01, DEF_STR( "1C_2C" ) );
            PORT_BIT(0x1c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x20, 0x00, "Sound");
            PORT_DIPSETTING(0x20, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x40, 0x00, "Sound Test");
            PORT_DIPSETTING(0x40, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x80, 0x80, "High Scores");
            PORT_DIPSETTING(0x00, "Keep Top 5");
            PORT_DIPSETTING(0x80, "Keep Top 10");

            PORT_START();
            /* IN1 */
            PORT_BIT(0x03, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME( 0x0c, 0x04, "Guesses" );
            PORT_DIPSETTING(    0x00, "2" );
            PORT_DIPSETTING(    0x04, "3" );
            PORT_DIPSETTING(    0x08, "4" );
            PORT_DIPSETTING(    0x0c, "5" );
            PORT_BIT(0x70, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_PLAYER1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_PLAYER1);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_PLAYER1);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_PLAYER1);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_START3);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_START4);
            PORT_BITX(0x10, IP_ACTIVE_LOW, IPT_BUTTON2, "Red Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BITX(0x20, IP_ACTIVE_LOW, IPT_BUTTON1, "Green Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X4();
            INPUT_PORTS_END();

        }
    };

    static InputPortPtr input_ports_triviag2 = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x03, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x03, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPSETTING(0x00, DEF_STR("Free_Play"));
            PORT_BIT(0x1c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x20, 0x00, "Sound");
            PORT_DIPSETTING(0x20, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x40, 0x00, "Sound Test");
            PORT_DIPSETTING(0x40, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x80, 0x80, "High Scores");
            PORT_DIPSETTING(0x00, "Keep Top 5");
            PORT_DIPSETTING(0x80, "Keep Top 10");

            PORT_START();
            /* IN1 */
            PORT_BIT(0x03, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x0c, 0x04, "Guesses");
            PORT_DIPSETTING(0x00, "3");
            PORT_DIPSETTING(0x04, "4");
            PORT_DIPSETTING(0x08, "5");
            PORT_DIPSETTING(0x0c, "6");
            PORT_BIT(0x70, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_PLAYER1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_PLAYER1);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_PLAYER1);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_PLAYER1);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_START3);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_START4);
            PORT_BITX(0x10, IP_ACTIVE_LOW, IPT_BUTTON2, "Red Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BITX(0x20, IP_ACTIVE_LOW, IPT_BUTTON1, "Green Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X4();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_triviasp = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x03, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x03, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPSETTING(0x00, DEF_STR("Free_Play"));
            PORT_BIT(0x1c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x20, 0x00, "Sound");
            PORT_DIPSETTING(0x20, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x40, 0x00, "Sound Test");
            PORT_DIPSETTING(0x40, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x80, 0x80, "High Scores");
            PORT_DIPSETTING(0x00, "Keep Top 5");
            PORT_DIPSETTING(0x80, "Keep Top 10");

            PORT_START();
            /* IN1 */
            PORT_BIT(0x03, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x0c, 0x04, "Guesses");
            PORT_DIPSETTING(0x00, "3");
            PORT_DIPSETTING(0x04, "4");
            PORT_DIPSETTING(0x08, "5");
            PORT_DIPSETTING(0x0c, "6");
            PORT_BIT(0x70, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_PLAYER1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_PLAYER1);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_PLAYER1);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_PLAYER1);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_START3);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_START4);
            PORT_BITX(0x10, IP_ACTIVE_LOW, IPT_BUTTON2, "Red Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BITX(0x20, IP_ACTIVE_LOW, IPT_BUTTON1, "Green Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X4();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_triviayp = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x03, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x03, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPSETTING(0x00, DEF_STR("Free_Play"));
            PORT_BIT(0x1c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x20, 0x00, "Sound");
            PORT_DIPSETTING(0x20, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x40, 0x00, "Sound Test");
            PORT_DIPSETTING(0x40, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x80, 0x80, "High Scores");
            PORT_DIPSETTING(0x00, "Keep Top 5");
            PORT_DIPSETTING(0x80, "Keep Top 10");

            PORT_START();
            /* IN1 */
            PORT_BIT(0x03, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x0c, 0x04, "Guesses");
            PORT_DIPSETTING(0x00, "3");
            PORT_DIPSETTING(0x04, "4");
            PORT_DIPSETTING(0x08, "5");
            PORT_DIPSETTING(0x0c, "6");
            PORT_BIT(0x70, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_PLAYER1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_PLAYER1);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_PLAYER1);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_PLAYER1);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_START3);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_START4);
            PORT_BITX(0x10, IP_ACTIVE_LOW, IPT_BUTTON2, "Red Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BITX(0x20, IP_ACTIVE_LOW, IPT_BUTTON1, "Green Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X4();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_triviabb = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x03, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x03, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPSETTING(0x00, DEF_STR("Free_Play"));
            PORT_BIT(0x1c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x20, 0x00, "Sound");
            PORT_DIPSETTING(0x20, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x40, 0x00, "Sound Test");
            PORT_DIPSETTING(0x40, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x80, 0x80, "High Scores");
            PORT_DIPSETTING(0x00, "Keep Top 5");
            PORT_DIPSETTING(0x80, "Keep Top 10");

            PORT_START();
            /* IN1 */
            PORT_BIT(0x03, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x0c, 0x04, "Guesses");
            PORT_DIPSETTING(0x00, "3");
            PORT_DIPSETTING(0x04, "4");
            PORT_DIPSETTING(0x08, "5");
            PORT_DIPSETTING(0x0c, "6");
            PORT_BIT(0x70, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_PLAYER1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_PLAYER1);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_PLAYER1);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_PLAYER1);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_START3);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_START4);
            PORT_BITX(0x10, IP_ACTIVE_LOW, IPT_BUTTON2, "Red Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BITX(0x20, IP_ACTIVE_LOW, IPT_BUTTON1, "Green Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X4();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_gimeabrk = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x01, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x03, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x02, DEF_STR("1C_2C"));
            PORT_DIPSETTING(0x00, DEF_STR("Free_Play"));
            PORT_DIPNAME(0x1c, 0x00, "Bonus Coins");
            PORT_DIPSETTING(0x00, "None");
            PORT_DIPSETTING(0x04, "2 Coins = 1 Bonus");
            PORT_DIPSETTING(0x08, "3 Coins = 1 Bonus");
            PORT_DIPSETTING(0x0c, "4 Coins = 1 Bonus");
            PORT_DIPSETTING(0x10, "4 Coins = 2 Bonus");
            PORT_DIPSETTING(0x14, "5 Coins = 1 Bonus");
            PORT_DIPSETTING(0x18, "5 Coins = 2 Bonus");
            PORT_DIPSETTING(0x1c, "5 Coins = 3 Bonus");
            PORT_DIPNAME(0x20, 0x00, "Left Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x20, "x2");
            PORT_DIPNAME(0xc0, 0x00, "Right Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x40, "x4");
            PORT_DIPSETTING(0x80, "x5");
            PORT_DIPSETTING(0xc0, "x6");

            PORT_START();
            /* IN1 */
            PORT_DIPNAME(0x03, 0x01, "Bonus Shot");
            PORT_DIPSETTING(0x00, "Every 6 Balls");
            PORT_DIPSETTING(0x01, "Every 8 Balls");
            PORT_DIPSETTING(0x02, "Every 10 Balls");
            PORT_DIPSETTING(0x03, "Every 12 Balls");
            PORT_DIPNAME(0x0c, 0x08, "Initial Shots");
            PORT_DIPSETTING(0x00, "8");
            PORT_DIPSETTING(0x04, "10");
            PORT_DIPSETTING(0x08, "12");
            PORT_DIPSETTING(0x0c, "14");
            PORT_DIPNAME(0x10, 0x00, "Players Per Credit");
            PORT_DIPSETTING(0x00, "1");
            PORT_DIPSETTING(0x10, "1 or 2");
            PORT_DIPNAME(0x20, 0x20, DEF_STR("Cabinet"));
            PORT_DIPSETTING(0x20, DEF_STR("Upright"));
            PORT_DIPSETTING(0x00, DEF_STR("Cocktail"));
            PORT_DIPNAME(0x40, 0x40, "High Scores");
            PORT_DIPSETTING(0x40, "Keep Top 5");
            PORT_DIPSETTING(0x00, "Keep All");
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x3f, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x30, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            PLAYER1_TRACKBALL();
            UNUSED_ANALOG_X2();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_minigolf = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x00, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x03, DEF_STR("3C_1C"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x00, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPNAME(0x1c, 0x00, "Bonus Coins");
            PORT_DIPSETTING(0x00, "None");
            PORT_DIPSETTING(0x04, "2 Coins = 1 Bonus");
            PORT_DIPSETTING(0x08, "3 Coins = 1 Bonus");
            PORT_DIPSETTING(0x0c, "4 Coins = 1 Bonus");
            PORT_DIPSETTING(0x10, "4 Coins = 2 Bonus");
            PORT_DIPSETTING(0x14, "5 Coins = 1 Bonus");
            PORT_DIPSETTING(0x18, "5 Coins = 2 Bonus");
            PORT_DIPSETTING(0x1c, "5 Coins = 3 Bonus");
            PORT_DIPNAME(0x20, 0x00, "Left Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x20, "x2");
            PORT_DIPNAME(0xc0, 0x00, "Right Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x40, "x4");
            PORT_DIPSETTING(0x80, "x5");
            PORT_DIPSETTING(0xc0, "x6");

            PORT_START();
            /* IN1 */
            PORT_DIPNAME(0x01, 0x01, "Add-A-Coin");
            PORT_DIPSETTING(0x01, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x02, 0x02, "Display Kids");
            PORT_DIPSETTING(0x02, DEF_STR("No"));
            PORT_DIPSETTING(0x00, DEF_STR("Yes"));
            PORT_DIPNAME(0x04, 0x04, "Kid on Left Located");
            PORT_DIPSETTING(0x04, DEF_STR("No"));
            PORT_DIPSETTING(0x00, DEF_STR("Yes"));
            PORT_DIPNAME(0x08, 0x08, "Kid on Right Located");
            PORT_DIPSETTING(0x08, DEF_STR("No"));
            PORT_DIPSETTING(0x00, DEF_STR("Yes"));
            PORT_DIPNAME(0x10, 0x10, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x10, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x20, 0x20, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x20, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x40, 0x40, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x40, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x3f, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x0c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X2();
            PLAYER1_TRACKBALL();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_minigol2 = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x01, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x03, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x02, DEF_STR("1C_2C"));
            PORT_DIPSETTING(0x00, DEF_STR("Free_Play"));
            PORT_DIPNAME(0x1c, 0x00, "Bonus Coins");
            PORT_DIPSETTING(0x00, "None");
            PORT_DIPSETTING(0x04, "2 Coins = 1 Bonus");
            PORT_DIPSETTING(0x08, "3 Coins = 1 Bonus");
            PORT_DIPSETTING(0x0c, "4 Coins = 1 Bonus");
            PORT_DIPSETTING(0x10, "4 Coins = 2 Bonus");
            PORT_DIPSETTING(0x14, "5 Coins = 1 Bonus");
            PORT_DIPSETTING(0x18, "5 Coins = 2 Bonus");
            PORT_DIPSETTING(0x1c, "5 Coins = 3 Bonus");
            PORT_DIPNAME(0x20, 0x00, "Left Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x20, "x2");
            PORT_DIPNAME(0xc0, 0x00, "Right Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x40, "x4");
            PORT_DIPSETTING(0x80, "x5");
            PORT_DIPSETTING(0xc0, "x6");

            PORT_START();
            /* IN1 */
            PORT_DIPNAME(0x01, 0x01, "Add-A-Coin");
            PORT_DIPSETTING(0x01, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x02, 0x02, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x02, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x04, 0x04, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x04, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x08, 0x08, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x08, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x10, 0x10, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x10, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x20, 0x20, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x20, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x40, 0x40, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x40, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x3f, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x0c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X2();
            PLAYER1_TRACKBALL();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_toggle = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x03, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x03, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPSETTING(0x00, DEF_STR("Free_Play"));
            PORT_BIT(0x7c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x80, "High Scores");
            PORT_DIPSETTING(0x80, "Keep Top 5");
            PORT_DIPSETTING(0x00, "Keep All");

            PORT_START();
            /* IN1 */
            PORT_BIT(0x03, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x04, 0x04, DEF_STR("Lives"));
            PORT_DIPSETTING(0x00, "2");
            PORT_DIPSETTING(0x04, "3");
            PORT_BIT(0x78, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_PLAYER1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_PLAYER1);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_PLAYER1);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_PLAYER1);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_PLAYER2);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_PLAYER2);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_PLAYER2);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_PLAYER2);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X4();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_nametune = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x00, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x03, DEF_STR("3C_2C"));
            PORT_DIPSETTING(0x00, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPNAME(0x1c, 0x00, "Bonus Coins");
            PORT_DIPSETTING(0x00, "None");
            PORT_DIPSETTING(0x04, "2 Coins = 1 Bonus");
            PORT_DIPSETTING(0x08, "3 Coins = 1 Bonus");
            PORT_DIPSETTING(0x0c, "4 Coins = 1 Bonus");
            PORT_DIPSETTING(0x10, "4 Coins = 2 Bonus");
            PORT_DIPSETTING(0x14, "5 Coins = 1 Bonus");
            PORT_DIPSETTING(0x18, "5 Coins = 2 Bonus");
            PORT_DIPSETTING(0x1c, "5 Coins = 3 Bonus");
            PORT_DIPNAME(0x20, 0x00, "Left Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x20, "x2");
            PORT_DIPNAME(0xc0, 0x00, "Right Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x40, "x4");
            PORT_DIPSETTING(0x80, "x5");
            PORT_DIPSETTING(0xc0, "x6");

            PORT_START();
            /* IN1 */
            PORT_DIPNAME(0x01, 0x01, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x01, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x02, 0x02, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x02, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x04, 0x04, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x04, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x08, 0x08, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x08, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x10, 0x10, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x10, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x20, 0x20, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x20, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x40, 0x40, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x40, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BITX(0x01, IP_ACTIVE_LOW, IPT_BUTTON4 | IPF_PLAYER1, "P1 Blue Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BITX(0x02, IP_ACTIVE_LOW, IPT_BUTTON3 | IPF_PLAYER1, "P1 Green Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BITX(0x04, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER1, "P1 Yellow Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BITX(0x08, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER1, "P1 Red Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BIT(0x30, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BITX(0x04, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2, "P2 Red Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BITX(0x08, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER2, "P2 Yellow Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BITX(0x10, IP_ACTIVE_LOW, IPT_BUTTON3 | IPF_PLAYER2, "P2 Green Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BITX(0x20, IP_ACTIVE_LOW, IPT_BUTTON4 | IPF_PLAYER2, "P2 Blue Button", IP_KEY_DEFAULT, IP_JOY_DEFAULT);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_LOW, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X4();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_nstocker = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x00, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x03, DEF_STR("3C_1C"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x00, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPNAME(0x1c, 0x00, "Bonus Coins");
            PORT_DIPSETTING(0x00, "None");
            PORT_DIPSETTING(0x04, "2 Coins = 1 Bonus");
            PORT_DIPSETTING(0x08, "3 Coins = 1 Bonus");
            PORT_DIPSETTING(0x0c, "4 Coins = 1 Bonus");
            PORT_DIPSETTING(0x10, "4 Coins = 2 Bonus");
            PORT_DIPSETTING(0x14, "5 Coins = 1 Bonus");
            PORT_DIPSETTING(0x18, "5 Coins = 2 Bonus");
            PORT_DIPSETTING(0x1c, "5 Coins = 3 Bonus");
            PORT_DIPNAME(0x20, 0x00, "Left Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x20, "x2");
            PORT_DIPNAME(0xc0, 0x00, "Right Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x40, "x4");
            PORT_DIPSETTING(0x80, "x5");
            PORT_DIPSETTING(0xc0, "x6");

            PORT_START();
            /* IN1 */
            PORT_DIPNAME(0x01, 0x00, DEF_STR("Difficulty"));
            PORT_DIPSETTING(0x01, "Easy");
            PORT_DIPSETTING(0x00, "Hard");
            PORT_BIT(0x7e, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x3f, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x3c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X3();
            /* cheese alert -- we have to map this to player 2 so that it doesn't interfere with */
 /* the crosshair controls */
            PORT_START();
            PORT_ANALOGX(0xff, 0, IPT_DIAL | IPF_PLAYER2 | IPF_CENTER, 100, 20, 0, 0,
                    KEYCODE_S, KEYCODE_F, JOYCODE_1_LEFT, JOYCODE_1_RIGHT);

            /* extra ports for shooters */
            PLAYER1_CROSSHAIRS();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_sfootbal = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x00, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x03, DEF_STR("3C_1C"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x00, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPNAME(0x1c, 0x00, "Bonus Coins");
            PORT_DIPSETTING(0x00, "None");
            PORT_DIPSETTING(0x04, "2 Coins = 1 Bonus");
            PORT_DIPSETTING(0x08, "3 Coins = 1 Bonus");
            PORT_DIPSETTING(0x0c, "4 Coins = 1 Bonus");
            PORT_DIPSETTING(0x10, "4 Coins = 2 Bonus");
            PORT_DIPSETTING(0x14, "5 Coins = 1 Bonus");
            PORT_DIPSETTING(0x18, "5 Coins = 2 Bonus");
            PORT_DIPSETTING(0x1c, "5 Coins = 3 Bonus");
            PORT_DIPNAME(0x20, 0x00, "Left Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x20, "x2");
            PORT_DIPNAME(0xc0, 0x00, "Right Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x40, "x4");
            PORT_DIPSETTING(0x80, "x5");
            PORT_DIPSETTING(0xc0, "x6");

            PORT_START();
            /* IN1 */
            PORT_DIPNAME(0x07, 0x03, "Game Duration");
            PORT_DIPSETTING(0x00, "1:30");
            PORT_DIPSETTING(0x01, "1:40");
            PORT_DIPSETTING(0x02, "1:50");
            PORT_DIPSETTING(0x03, "2:00");
            PORT_DIPSETTING(0x04, "2:20");
            PORT_DIPSETTING(0x05, "2:40");
            PORT_DIPSETTING(0x06, "3:00");
            PORT_DIPSETTING(0x07, "3:30");
            PORT_DIPNAME(0x08, 0x00, "Players Per Credit");
            PORT_DIPSETTING(0x00, "1");
            PORT_DIPSETTING(0x08, "1 or 2");
            PORT_BIT(0x70, IP_ACTIVE_HIGH, IPT_UNUSED);
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2);
            PORT_BIT(0x3c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x3c, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            PLAYER2_ANALOG_JOYSTICK();
            PLAYER1_ANALOG_JOYSTICK();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_spiker = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x00, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x03, DEF_STR("3C_1C"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x00, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPNAME(0x1c, 0x00, "Bonus Coins");
            PORT_DIPSETTING(0x00, "None");
            PORT_DIPSETTING(0x04, "2 Coins = 1 Bonus");
            PORT_DIPSETTING(0x08, "3 Coins = 1 Bonus");
            PORT_DIPSETTING(0x0c, "4 Coins = 1 Bonus");
            PORT_DIPSETTING(0x10, "4 Coins = 2 Bonus");
            PORT_DIPSETTING(0x14, "5 Coins = 1 Bonus");
            PORT_DIPSETTING(0x18, "5 Coins = 2 Bonus");
            PORT_DIPSETTING(0x1c, "5 Coins = 3 Bonus");
            PORT_DIPNAME(0x20, 0x00, "Left Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x20, "x2");
            PORT_DIPNAME(0xc0, 0x00, "Right Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x40, "x4");
            PORT_DIPSETTING(0x80, "x5");
            PORT_DIPSETTING(0xc0, "x6");

            PORT_START();
            /* IN1 */
            PORT_DIPNAME(0x01, 0x00, "Game Duration");
            PORT_DIPSETTING(0x00, "11 points");
            PORT_DIPSETTING(0x01, "15 points");
            PORT_DIPNAME(0x02, 0x02, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x02, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x04, 0x04, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x04, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x08, 0x08, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x08, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x10, 0x10, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x10, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x20, 0x20, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x20, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x40, 0x40, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x40, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x0f, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER1);
            PORT_BIT(0x38, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            PLAYER2_TRACKBALL();
            PLAYER1_TRACKBALL();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_stompin = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x00, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x03, DEF_STR("3C_1C"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x00, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPNAME(0x1c, 0x00, "Bonus Coins");
            PORT_DIPSETTING(0x00, "None");
            PORT_DIPSETTING(0x04, "2 Coins = 1 Bonus");
            PORT_DIPSETTING(0x08, "3 Coins = 1 Bonus");
            PORT_DIPSETTING(0x0c, "4 Coins = 1 Bonus");
            PORT_DIPSETTING(0x10, "4 Coins = 2 Bonus");
            PORT_DIPSETTING(0x14, "5 Coins = 1 Bonus");
            PORT_DIPSETTING(0x18, "5 Coins = 2 Bonus");
            PORT_DIPSETTING(0x1c, "5 Coins = 3 Bonus");
            PORT_DIPNAME(0x20, 0x00, "Left Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x20, "x2");
            PORT_DIPNAME(0xc0, 0x00, "Right Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x40, "x4");
            PORT_DIPSETTING(0x80, "x5");
            PORT_DIPSETTING(0xc0, "x6");

            PORT_START();
            /* IN1 */
            PORT_DIPNAME(0x01, 0x00, "Display Kids");
            PORT_DIPSETTING(0x00, DEF_STR("No"));
            PORT_DIPSETTING(0x01, DEF_STR("Yes"));
            PORT_DIPNAME(0x02, 0x02, "Kid on Right Located");
            PORT_DIPSETTING(0x02, DEF_STR("No"));
            PORT_DIPSETTING(0x00, DEF_STR("Yes"));
            PORT_DIPNAME(0x04, 0x04, "Kid on Left Located");
            PORT_DIPSETTING(0x04, DEF_STR("No"));
            PORT_DIPSETTING(0x00, DEF_STR("Yes"));
            PORT_DIPNAME(0x08, 0x08, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x08, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x10, 0x10, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x10, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x20, 0x20, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x00, DEF_STR("Off"));
            PORT_DIPSETTING(0x20, DEF_STR("On"));
            PORT_DIPNAME(0x40, 0x00, "Bee In Game");
            PORT_DIPSETTING(0x40, DEF_STR("No"));
            PORT_DIPSETTING(0x00, DEF_STR("Yes"));
            PORT_DIPNAME(0x80, 0x00, "Bug Generation");
            PORT_DIPSETTING(0x00, "Regular");
            PORT_DIPSETTING(0x80, "None");

            PORT_START();
            /* IN2 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_JOYSTICKRIGHT_UP | IPF_PLAYER1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_JOYSTICKRIGHT_DOWN | IPF_PLAYER1);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_JOYSTICKRIGHT_RIGHT | IPF_PLAYER1);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_JOYSTICKRIGHT_LEFT | IPF_PLAYER1);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_UP | IPF_PLAYER1);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_DOWN | IPF_PLAYER1);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_RIGHT | IPF_PLAYER1);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_LEFT | IPF_PLAYER1);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* "analog" ports */
            PORT_START();
            PORT_BIT(0x1f, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BITX(0x20, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER1, "Top-Right", KEYCODE_9_PAD, IP_JOY_DEFAULT);
            PORT_BITX(0x40, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER1, "Top", KEYCODE_8_PAD, IP_JOY_DEFAULT);
            PORT_BITX(0x80, IP_ACTIVE_LOW, IPT_BUTTON3 | IPF_PLAYER1, "Top-Left", KEYCODE_7_PAD, IP_JOY_DEFAULT);

            PORT_START();
            PORT_BIT(0x1f, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BITX(0x20, IP_ACTIVE_LOW, IPT_BUTTON4 | IPF_PLAYER1, "Right", KEYCODE_6_PAD, IP_JOY_DEFAULT);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BITX(0x80, IP_ACTIVE_LOW, IPT_BUTTON5 | IPF_PLAYER1, "Left", KEYCODE_4_PAD, IP_JOY_DEFAULT);

            PORT_START();
            PORT_BIT(0x1f, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BITX(0x20, IP_ACTIVE_LOW, IPT_BUTTON6 | IPF_PLAYER1, "Bot-Right", KEYCODE_3_PAD, IP_JOY_DEFAULT);
            PORT_BITX(0x40, IP_ACTIVE_LOW, IPT_BUTTON7 | IPF_PLAYER1, "Bottom", KEYCODE_2_PAD, IP_JOY_DEFAULT);
            PORT_BITX(0x80, IP_ACTIVE_LOW, IPT_BUTTON8 | IPF_PLAYER1, "Bot-Left", KEYCODE_1_PAD, IP_JOY_DEFAULT);

            UNUSED_ANALOG();
            INPUT_PORTS_END();
        }
    };

    static InputPortPtr input_ports_rescraid = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* IN0 */
            PORT_DIPNAME(0x03, 0x00, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x03, DEF_STR("3C_1C"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x00, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPNAME(0x1c, 0x00, "Bonus Coins");
            PORT_DIPSETTING(0x00, "None");
            PORT_DIPSETTING(0x04, "2 Coins = 1 Bonus");
            PORT_DIPSETTING(0x08, "3 Coins = 1 Bonus");
            PORT_DIPSETTING(0x0c, "4 Coins = 1 Bonus");
            PORT_DIPSETTING(0x10, "4 Coins = 2 Bonus");
            PORT_DIPSETTING(0x14, "5 Coins = 1 Bonus");
            PORT_DIPSETTING(0x18, "5 Coins = 2 Bonus");
            PORT_DIPSETTING(0x1c, "5 Coins = 3 Bonus");
            PORT_DIPNAME(0x20, 0x00, "Left Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x20, "x2");
            PORT_DIPNAME(0xc0, 0x00, "Right Coin Mech");
            PORT_DIPSETTING(0x00, "x1");
            PORT_DIPSETTING(0x40, "x4");
            PORT_DIPSETTING(0x80, "x5");
            PORT_DIPSETTING(0xc0, "x6");

            PORT_START();
            /* IN1 */
            PORT_DIPNAME(0x01, 0x01, DEF_STR("Lives"));
            PORT_DIPSETTING(0x00, "4");
            PORT_DIPSETTING(0x01, "5");
            PORT_DIPNAME(0x0c, 0x04, "Minimum Game Time");
            PORT_DIPSETTING(0x08, "45");
            PORT_DIPSETTING(0x04, "60");
            PORT_DIPSETTING(0x00, "90");
            PORT_DIPSETTING(0x0c, "120");
            PORT_DIPNAME(0x10, 0x10, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x10, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x20, 0x20, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x20, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x40, 0x40, "Keep High Scores");
            PORT_DIPSETTING(0x40, DEF_STR("No"));
            PORT_DIPSETTING(0x00, DEF_STR("Yes"));
            PORT_DIPNAME(0x80, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x80, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));

            PORT_START();
            /* IN2 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_JOYSTICKRIGHT_UP | IPF_PLAYER1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_JOYSTICKRIGHT_DOWN | IPF_PLAYER1);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_JOYSTICKRIGHT_RIGHT | IPF_PLAYER1);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_JOYSTICKRIGHT_LEFT | IPF_PLAYER1);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_SERVICE(0x80, IP_ACTIVE_LOW);

            PORT_START();
            /* IN3 */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_START2);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_UP | IPF_PLAYER1);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_DOWN | IPF_PLAYER1);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_RIGHT | IPF_PLAYER1);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_LEFT | IPF_PLAYER1);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN2);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_VBLANK);

            /* analog ports */
            UNUSED_ANALOG_X4();
            INPUT_PORTS_END();
        }
    };

    /**
     * ***********************************
     *
     * Sound definitions
     *
     ************************************
     */
    static cem3394_interface cem_interface = new cem3394_interface(
            6,
            new int[]{90, 90, 90, 90, 90, 90},
            new double[]{431.894, 431.894, 431.894, 431.894, 431.894, 431.894},
            new double[]{1300.0, 1300.0, 1300.0, 1300.0, 1300.0, 1300.0},
            new externalPtr[]{noise_gen, noise_gen, noise_gen, noise_gen, noise_gen, noise_gen}
    );

    /**
     * ***********************************
     *
     * Machine driver
     *
     ************************************
     */
    static MachineDriver machine_driver_balsente = new MachineDriver(
            /* basic machine hardware */
            new MachineCPU[]{
                new MachineCPU(
                        CPU_M6809,
                        5000000 / 4, /* 5MHz/4 */
                        readmem_cpu1, writemem_cpu1, null, null,
                        update_analog_inputs, 1
                ),
                new MachineCPU(
                        CPU_Z80 | CPU_AUDIO_CPU,
                        4000000, /* 4MHz */
                        readmem_cpu2, writemem_cpu2, readport_cpu2, writeport_cpu2,
                        ignore_interrupt, 1
                )
            },
            60, DEFAULT_REAL_60HZ_VBLANK_DURATION, /* frames per second, vblank duration */
            10,
            init_machine,
            /* video hardware */
            256, 240, new rectangle(0, 255, 0, 239),
            null,
            1024, 0,
            null,
            VIDEO_TYPE_RASTER | VIDEO_UPDATE_BEFORE_VBLANK,
            null,
            balsente_vh_start,
            balsente_vh_stop,
            balsente_vh_screenrefresh,
            /* sound hardware */
            0, 0, 0, 0,
            new MachineSound[]{
                new MachineSound(SOUND_CEM3394, cem_interface)
            },
            nvram_handler
    );

    /**
     * ***********************************
     *
     * Driver initialization
     *
     ************************************
     */
    public static int EXPAND_ALL = 0x00;
    public static int EXPAND_NONE = 0x3f;
    public static int SWAP_HALVES = 0x80;

    static void expand_roms(int cd_rom_mask) {
        /* load AB bank data from 0x10000-0x20000 */
 /* load CD bank data from 0x20000-0x2e000 */
 /* load EF           from 0x2e000-0x30000 */
 /* ROM region must be 0x40000 total */

        UBytePtr temp = new UBytePtr(0x20000);
        if (temp != null) {
            UBytePtr rom = memory_region(REGION_CPU1);
            int/*UINT32*/ base;

            for (base = 0x10000; base < memory_region_length(REGION_CPU1); base += 0x30000) {
                UBytePtr ab_base = new UBytePtr(temp, 0x00000);
                UBytePtr cd_base = new UBytePtr(temp, 0x10000);
                UBytePtr cd_common = new UBytePtr(temp, 0x1c000);
                UBytePtr ef_common = new UBytePtr(temp, 0x1e000);
                int/*UINT32*/ dest;

                for (dest = 0x00000; dest < 0x20000; dest += 0x02000) {
                    if ((cd_rom_mask & SWAP_HALVES) != 0) {
                        memcpy(temp, dest ^ 0x02000, rom, base + dest, 0x02000);
                    } else {
                        memcpy(temp, dest, rom, base + dest, 0x02000);
                    }
                }

                memcpy(rom, base + 0x2e000, ef_common, 0x2000);
                memcpy(rom, base + 0x2c000, cd_common, 0x2000);
                memcpy(rom, base + 0x2a000, ab_base, 0xe000, 0x2000);

                memcpy(rom, base + 0x28000, ef_common, 0x2000);
                memcpy(rom, base + 0x26000, cd_common, 0x2000);
                memcpy(rom, base + 0x24000, ab_base, 0xc000, 0x2000);

                memcpy(rom, base + 0x22000, ef_common, 0x2000);
                if ((cd_rom_mask & 0x20) != 0) {
                    memcpy(rom, base + 0x20000, cd_base, 0xa000, 0x2000);
                } else {
                    memcpy(rom, base + 0x20000, cd_common, 0x2000);
                }
                memcpy(rom, base + 0x1e000, ab_base, 0xa000, 0x2000);

                memcpy(rom, base + 0x1c000, ef_common, 0x2000);
                if ((cd_rom_mask & 0x10) != 0) {
                    memcpy(rom, base + 0x1a000, cd_base, 0x8000, 0x2000);
                } else {
                    memcpy(rom, base + 0x1a000, cd_common, 0x2000);
                }
                memcpy(rom, base + 0x18000, ab_base, 0x8000, 0x2000);

                memcpy(rom, base + 0x16000, ef_common, 0x2000);
                if ((cd_rom_mask & 0x08) != 0) {
                    memcpy(rom, base + 0x14000, cd_base, 0x6000, 0x2000);
                } else {
                    memcpy(rom, base + 0x14000, cd_common, 0x2000);
                }
                memcpy(rom, base + 0x12000, ab_base, 0x6000, 0x2000);

                memcpy(rom, base + 0x10000, ef_common, 0x2000);
                if ((cd_rom_mask & 0x04) != 0) {
                    memcpy(rom, base + 0x0e000, cd_base, 0x4000, 0x2000);
                } else {
                    memcpy(rom, base + 0x0e000, cd_common, 0x2000);
                }
                memcpy(rom, base + 0x0c000, ab_base, 0x4000, 0x2000);

                memcpy(rom, base + 0x0a000, ef_common, 0x2000);
                if ((cd_rom_mask & 0x02) != 0) {
                    memcpy(rom, base + 0x08000, cd_base, 0x2000, 0x2000);
                } else {
                    memcpy(rom, base + 0x08000, cd_common, 0x2000);
                }
                memcpy(rom, base + 0x06000, ab_base, 0x2000, 0x2000);

                memcpy(rom, base + 0x04000, ef_common, 0x2000);
                if ((cd_rom_mask & 0x01) != 0) {
                    memcpy(rom, base + 0x02000, cd_base, 0x0000, 0x2000);
                } else {
                    memcpy(rom, base + 0x02000, cd_common, 0x2000);
                }
                memcpy(rom, base + 0x00000, ab_base, 0x0000, 0x2000);
            }

            temp = null;
        }
    }

    public static InitDriverPtr init_sentetst = new InitDriverPtr() {
        public void handler() {
            expand_roms(EXPAND_ALL);
            balsente_shooter = 0;
            /* noanalog */ }
    };
    public static InitDriverPtr init_cshift = new InitDriverPtr() {
        public void handler() {
            expand_roms(EXPAND_ALL);
            balsente_shooter = 0;
            /* noanalog */ }
    };
    public static InitDriverPtr init_gghost = new InitDriverPtr() {
        public void handler() {
            expand_roms(EXPAND_ALL);
            balsente_shooter = 0;
            u8_adc_shift = 1;
        }
    };
    public static InitDriverPtr init_hattrick = new InitDriverPtr() {
        public void handler() {
            expand_roms(EXPAND_ALL);
            balsente_shooter = 0;
            /* noanalog */ }
    };
    public static InitDriverPtr init_otwalls = new InitDriverPtr() {
        public void handler() {
            expand_roms(EXPAND_ALL);
            balsente_shooter = 0;
            u8_adc_shift = 0;
        }
    };
    public static InitDriverPtr init_snakepit = new InitDriverPtr() {
        public void handler() {
            expand_roms(EXPAND_ALL);
            balsente_shooter = 0;
            u8_adc_shift = 1;
        }
    };
    public static InitDriverPtr init_snakjack = new InitDriverPtr() {
        public void handler() {
            expand_roms(EXPAND_ALL);
            balsente_shooter = 0;
            u8_adc_shift = 1;
        }
    };
    public static InitDriverPtr init_stocker = new InitDriverPtr() {
        public void handler() {
            expand_roms(EXPAND_ALL);
            balsente_shooter = 0;
            u8_adc_shift = 0;
        }
    };
    public static InitDriverPtr init_triviag1 = new InitDriverPtr() {
        public void handler() {
            expand_roms(EXPAND_ALL);
            balsente_shooter = 0;
            /* noanalog */ }
    };
    public static InitDriverPtr init_triviag2 = new InitDriverPtr() {
        public void handler() {
            memcpy(memory_region(REGION_CPU1), 0x20000, memory_region(REGION_CPU1), 0x28000, 0x4000);
            memcpy(memory_region(REGION_CPU1), 0x24000, memory_region(REGION_CPU1), 0x28000, 0x4000);
            expand_roms(EXPAND_NONE);
            balsente_shooter = 0;
            /* noanalog */
        }
    };
    
    public static InitDriverPtr init_triviaes = new InitDriverPtr() {
        public void handler() {
            expand_roms(EXPAND_NONE | SWAP_HALVES);
            balsente_shooter = 0;
            /* noanalog */ }
    };
    public static InitDriverPtr init_gimeabrk = new InitDriverPtr() {
        public void handler() {
            expand_roms(EXPAND_ALL);
            balsente_shooter = 0;
            u8_adc_shift = 1;
        }
    };
    public static InitDriverPtr init_minigolf = new InitDriverPtr() {
        public void handler() {
            expand_roms(EXPAND_NONE);
            balsente_shooter = 0;
            u8_adc_shift = 2;
        }
    };
    public static InitDriverPtr init_minigol2 = new InitDriverPtr() {
        public void handler() {
            expand_roms(0x0c);
            balsente_shooter = 0;
            u8_adc_shift = 2;
        }
    };
    public static InitDriverPtr init_toggle = new InitDriverPtr() {
        public void handler() {
            expand_roms(EXPAND_ALL);
            balsente_shooter = 0;
            /* noanalog */ }
    };
    public static InitDriverPtr init_nametune = new InitDriverPtr() {
        public void handler() {
            expand_roms(EXPAND_NONE | SWAP_HALVES);
            balsente_shooter = 0;
            /* noanalog */ }
    };
    public static InitDriverPtr init_nstocker = new InitDriverPtr() {
        public void handler() {
            install_mem_read_handler(0, 0x9902, 0x9902, nstocker_port2_r);
            expand_roms(EXPAND_NONE | SWAP_HALVES);
            balsente_shooter = 1;
            u8_adc_shift = 1;
        }
    };
    public static InitDriverPtr init_sfootbal = new InitDriverPtr() {
        public void handler() {
            expand_roms(EXPAND_ALL | SWAP_HALVES);
            balsente_shooter = 0;
            u8_adc_shift = 0;
        }
    };
    public static InitDriverPtr init_spiker = new InitDriverPtr() {
        public void handler() {
            install_mem_write_handler(0, 0x9f80, 0x9f8f, spiker_expand_w);
            install_mem_read_handler(0, 0x9f80, 0x9f8f, spiker_expand_r);
            expand_roms(EXPAND_ALL | SWAP_HALVES);
            balsente_shooter = 0;
            u8_adc_shift = 1;
        }
    };
    public static InitDriverPtr init_stompin = new InitDriverPtr() {
        public void handler() {
            expand_roms(0x0c | SWAP_HALVES);
            balsente_shooter = 0;
            u8_adc_shift = 32;
        }
    };
    public static InitDriverPtr init_rescraid = new InitDriverPtr() {
        public void handler() {
            expand_roms(EXPAND_NONE);
            balsente_shooter = 0;
            /* noanalog */ }
    };

    /**
     * ***********************************
     *
     * ROM definitions
     *
     ************************************
     */
    static RomLoadPtr rom_sentetst = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("sdiagef.bin", 0x2e000, 0x2000, 0x2a39fc53);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("sdiaggr0.bin", 0x00000, 0x2000, 0x5e0ff62a);
            ROM_END();
        }
    };

    static RomLoadPtr rom_cshift = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("cs-ab0.bin", 0x10000, 0x2000, 0xd2069e75);
            ROM_LOAD("cs-ab1.bin", 0x12000, 0x2000, 0x198f25a8);
            ROM_LOAD("cs-ab2.bin", 0x14000, 0x2000, 0x2e2b2b82);
            ROM_LOAD("cs-ab3.bin", 0x16000, 0x2000, 0xb97fc520);
            ROM_LOAD("cs-ab4.bin", 0x18000, 0x2000, 0xb4f0d673);
            ROM_LOAD("cs-ab5.bin", 0x1a000, 0x2000, 0xb1f8e589);
            ROM_LOAD("cs-cd.bin", 0x2c000, 0x2000, 0xf555a0b2);
            ROM_LOAD("cs-ef.bin", 0x2e000, 0x2000, 0x368b1ce3);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("cs-gr0.bin", 0x00000, 0x2000, 0x67f9d3b3);
            ROM_LOAD("cs-gr1.bin", 0x02000, 0x2000, 0x78973d50);
            ROM_LOAD("cs-gr2.bin", 0x04000, 0x2000, 0x1784f939);
            ROM_LOAD("cs-gr3.bin", 0x06000, 0x2000, 0xb43916a2);
            ROM_LOAD("cs-gr4.bin", 0x08000, 0x2000, 0xa94cd35b);
            ROM_END();
        }
    };

    static RomLoadPtr rom_gghost = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("ggh-ab0.bin", 0x10000, 0x2000, 0xed0fdeac);
            ROM_LOAD("ggh-ab1.bin", 0x12000, 0x2000, 0x5bfbae58);
            ROM_LOAD("ggh-ab2.bin", 0x14000, 0x2000, 0xf0baf921);
            ROM_LOAD("ggh-ab3.bin", 0x16000, 0x2000, 0xed0fdeac);
            ROM_LOAD("ggh-ab4.bin", 0x18000, 0x2000, 0x5bfbae58);
            ROM_LOAD("ggh-ab5.bin", 0x1a000, 0x2000, 0xf0baf921);
            ROM_LOAD("ggh-cd.bin", 0x2c000, 0x2000, 0xd3d75f84);
            ROM_LOAD("ggh-ef.bin", 0x2e000, 0x2000, 0xa02b4243);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("ggh-gr0.bin", 0x00000, 0x2000, 0x03515526);
            ROM_LOAD("ggh-gr1.bin", 0x02000, 0x2000, 0xb4293435);
            ROM_LOAD("ggh-gr2.bin", 0x04000, 0x2000, 0xece0cb97);
            ROM_LOAD("ggh-gr3.bin", 0x06000, 0x2000, 0xdd7e25d0);
            ROM_LOAD("ggh-gr4.bin", 0x08000, 0x2000, 0xb4293435);
            ROM_LOAD("ggh-gr5.bin", 0x0a000, 0x2000, 0xd3da0093);
            ROM_END();
        }
    };

    static RomLoadPtr rom_hattrick = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("rom-ab0.u9a", 0x10000, 0x2000, 0xf25c1b99);
            ROM_LOAD("rom-ab1.u8a", 0x12000, 0x2000, 0xc1df3d1f);
            ROM_LOAD("rom-ab2.u7a", 0x14000, 0x2000, 0xf6c41257);
            ROM_LOAD("rom-cd.u3a", 0x2c000, 0x2000, 0xfc44f36c);
            ROM_LOAD("rom-ef.u2a", 0x2e000, 0x2000, 0xd8f910fb);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("rom-gr0.u9b", 0x00000, 0x2000, 0x9f41baba);
            ROM_LOAD("rom-gr1.u8b", 0x02000, 0x2000, 0x951f08c9);
            ROM_END();
        }
    };

    static RomLoadPtr rom_otwalls = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("otw-ab0.bin", 0x10000, 0x2000, 0x474441c7);
            ROM_LOAD("otw-ab1.bin", 0x12000, 0x2000, 0x2e9e9411);
            ROM_LOAD("otw-ab2.bin", 0x14000, 0x2000, 0xba092128);
            ROM_LOAD("otw-ab3.bin", 0x16000, 0x2000, 0x74bc479d);
            ROM_LOAD("otw-ab4.bin", 0x18000, 0x2000, 0xf5f67619);
            ROM_LOAD("otw-ab5.bin", 0x1a000, 0x2000, 0xf5f67619);
            ROM_LOAD("otw-cd.bin", 0x2c000, 0x2000, 0x8e2d15ab);
            ROM_LOAD("otw-ef.bin", 0x2e000, 0x2000, 0x57eab299);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("otw-gr0.bin", 0x00000, 0x2000, 0x210bad3c);
            ROM_LOAD("otw-gr1.bin", 0x02000, 0x2000, 0x13e6aaa5);
            ROM_LOAD("otw-gr2.bin", 0x04000, 0x2000, 0x5cfefee5);
            ROM_LOAD("otw-gr3.bin", 0x06000, 0x2000, 0x6b17e4a9);
            ROM_LOAD("otw-gr4.bin", 0x08000, 0x2000, 0x15985c8c);
            ROM_LOAD("otw-gr5.bin", 0x0a000, 0x2000, 0x448f7e3c);
            ROM_END();
        }
    };

    static RomLoadPtr rom_snakepit = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("spit-ab0.bin", 0x10000, 0x2000, 0x5aa86081);
            ROM_LOAD("spit-ab1.bin", 0x12000, 0x2000, 0x588228b8);
            ROM_LOAD("spit-ab2.bin", 0x14000, 0x2000, 0x60173ab6);
            ROM_LOAD("spit-ab3.bin", 0x16000, 0x2000, 0x56cb51a8);
            ROM_LOAD("spit-ab4.bin", 0x18000, 0x2000, 0x40ba61e0);
            ROM_LOAD("spit-ab5.bin", 0x1a000, 0x2000, 0x2a1d9d8f);
            ROM_LOAD("spit-cd.bin", 0x2c000, 0x2000, 0x54095cbb);
            ROM_LOAD("spit-ef.bin", 0x2e000, 0x2000, 0x5f836a66);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("spit-gr0.bin", 0x00000, 0x2000, 0xf77fd85d);
            ROM_LOAD("spit-gr1.bin", 0x02000, 0x2000, 0x3ad10334);
            ROM_LOAD("spit-gr2.bin", 0x04000, 0x2000, 0x24887703);
            ROM_LOAD("spit-gr3.bin", 0x06000, 0x2000, 0xc6703ec2);
            ROM_LOAD("spit-gr4.bin", 0x08000, 0x2000, 0xb4293435);
            ROM_LOAD("spit-gr5.bin", 0x0a000, 0x2000, 0xdc27c970);
            ROM_END();
        }
    };

    static RomLoadPtr rom_snakjack = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("rom-ab0.u9a", 0x10000, 0x2000, 0xda2dd119);
            ROM_LOAD("rom-ab1.u8a", 0x12000, 0x2000, 0x657ddf26);
            ROM_LOAD("rom-ab2.u7a", 0x14000, 0x2000, 0x15333dcf);
            ROM_LOAD("rom-ab3.u6a", 0x16000, 0x2000, 0x57671f6f);
            ROM_LOAD("rom-ab4.u5a", 0x18000, 0x2000, 0xc16c5dc0);
            ROM_LOAD("rom-ab5.u4a", 0x1a000, 0x2000, 0xd7019747);
            ROM_LOAD("rom-cd.u3a", 0x2c000, 0x2000, 0x7b44ca4c);
            ROM_LOAD("rom-ef.u1a", 0x2e000, 0x2000, 0xf5309b38);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("rom-gr0.u9b", 0x00000, 0x2000, 0x3e64b5d5);
            ROM_LOAD("rom-gr1.u8b", 0x02000, 0x2000, 0xb3b8baee);
            ROM_LOAD("rom-gr2.u7b", 0x04000, 0x2000, 0xe9d89dac);
            ROM_LOAD("rom-gr3.u6b", 0x06000, 0x2000, 0xb6602be8);
            ROM_LOAD("rom-gr4.u5b", 0x08000, 0x2000, 0x3fbfa686);
            ROM_LOAD("rom-gr5.u4b", 0x0a000, 0x2000, 0x345f94fb);
            ROM_END();
        }
    };

    static RomLoadPtr rom_stocker = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("stkr-ab0.bin", 0x10000, 0x2000, 0x784a00ad);
            ROM_LOAD("stkr-ab1.bin", 0x12000, 0x2000, 0xcdae01dc);
            ROM_LOAD("stkr-ab2.bin", 0x14000, 0x2000, 0x18527d57);
            ROM_LOAD("stkr-ab3.bin", 0x16000, 0x2000, 0x028f6c06);
            ROM_LOAD("stkr-cd.bin", 0x2c000, 0x2000, 0x53dbc4e5);
            ROM_LOAD("stkr-ef.bin", 0x2e000, 0x2000, 0xcdcf46bc);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("stkr-gr0.bin", 0x00000, 0x2000, 0x76d5694c);
            ROM_LOAD("stkr-gr1.bin", 0x02000, 0x2000, 0x4a5cc00b);
            ROM_LOAD("stkr-gr2.bin", 0x04000, 0x2000, 0x70002382);
            ROM_LOAD("stkr-gr3.bin", 0x06000, 0x2000, 0x68c862d8);
            ROM_END();
        }
    };

    static RomLoadPtr rom_triviag1 = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("tpg1-ab0.bin", 0x10000, 0x2000, 0x79fd3ac3);
            ROM_LOAD("tpg1-ab1.bin", 0x12000, 0x2000, 0x0ff677e9);
            ROM_LOAD("tpg1-ab2.bin", 0x14000, 0x2000, 0x3b4d03e7);
            ROM_LOAD("tpg1-ab3.bin", 0x16000, 0x2000, 0x2c6c0651);
            ROM_LOAD("tpg1-ab4.bin", 0x18000, 0x2000, 0x397529e7);
            ROM_LOAD("tpg1-ab5.bin", 0x1a000, 0x2000, 0x499773a4);
            ROM_LOAD("tpg1-cd.bin", 0x2c000, 0x2000, 0x35c9b9c2);
            ROM_LOAD("tpg1-ef.bin", 0x2e000, 0x2000, 0x64878342);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("tpg1-gr0.bin", 0x00000, 0x2000, 0x20c9217a);
            ROM_LOAD("tpg1-gr1.bin", 0x02000, 0x2000, 0xd7f44504);
            ROM_LOAD("tpg1-gr2.bin", 0x04000, 0x2000, 0x4e59a15d);
            ROM_LOAD("tpg1-gr3.bin", 0x06000, 0x2000, 0x323a8640);
            ROM_LOAD("tpg1-gr4.bin", 0x08000, 0x2000, 0x673acf42);
            ROM_LOAD("tpg1-gr5.bin", 0x0a000, 0x2000, 0x067bfd66);
            ROM_END();
        }
    };
    
    static RomLoadPtr rom_triviaes = new RomLoadPtr() {
        public void handler() {
        
	
            ROM_REGION(0x40000, REGION_CPU1, 0);
            ROM_LOAD( "tp_a2.bin",  0x10000, 0x04000, 0xb4d69463);
            ROM_LOAD( "tp_a7.bin",  0x14000, 0x04000, 0xd78bd4b6);
            ROM_LOAD( "tp_a4.bin",  0x18000, 0x04000, 0x0de9e14d);
            ROM_LOAD( "tp_a5.bin",  0x1c000, 0x04000, 0xe749adac);
            ROM_LOAD( "tp_a8.bin",  0x20000, 0x04000, 0x168ef5ed);
            ROM_LOAD( "tp_a1.bin",  0x24000, 0x04000, 0x1f6ef37f);
            ROM_LOAD( "tp_a6.bin",  0x28000, 0x04000, 0x421c1a29);
            ROM_LOAD( "tp_a3.bin",  0x2c000, 0x04000, 0xc6254f46);

            ROM_REGION( 0x10000, REGION_CPU2, 0 );		/* 64k for Z80 */
            ROM_LOAD( "tpsonido.bin",  0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION( 0x10000, REGION_GFX1, 0 );		/* up to 64k of sprites */
            ROM_LOAD( "tp_gr3.bin", 0x00000, 0x4000, 0x6829de8e);
            ROM_LOAD( "tp_gr2.bin", 0x04000, 0x4000, 0x89398700);
            ROM_LOAD( "tp_gr1.bin", 0x08000, 0x4000, 0x1242033e);
            ROM_END();
        }
    };

    static RomLoadPtr rom_triviag2 = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("ab01.bin", 0x10000, 0x4000, 0x4fca20c5);
            ROM_LOAD("ab23.bin", 0x14000, 0x4000, 0x6cf2ddeb);
            ROM_LOAD("ab45.bin", 0x18000, 0x4000, 0xa7ff789c);
            ROM_LOAD("ab67.bin", 0x1c000, 0x4000, 0xcc5c68ef);
            ROM_LOAD("cd45.bin", 0x28000, 0x4000, 0xfc9c752a);
            ROM_LOAD("cd6ef.bin", 0x2c000, 0x4000, 0x23b56fb8);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("gr01.bin", 0x00000, 0x4000, 0x6829de8e);
            ROM_LOAD("gr23.bin", 0x04000, 0x4000, 0x89398700);
            ROM_LOAD("gr45.bin", 0x08000, 0x4000, 0x1e870293);
            ROM_END();
        }
    };

    static RomLoadPtr rom_triviasp = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("allsport.8a", 0x10000, 0x4000, 0x54b7ff31);
            ROM_LOAD("allsport.7a", 0x14000, 0x4000, 0x59fae9d2);
            ROM_LOAD("allsport.6a", 0x18000, 0x4000, 0x237b6b95);
            ROM_LOAD("allsport.5a", 0x1c000, 0x4000, 0xb64d7f61);
            ROM_LOAD("allsport.3a", 0x28000, 0x4000, 0xe45d09d6);
            ROM_LOAD("allsport.1a", 0x2c000, 0x4000, 0x8bb3e831);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("gr01.bin", 0x00000, 0x4000, 0x6829de8e);
            ROM_LOAD("gr23.bin", 0x04000, 0x4000, 0x89398700);
            ROM_LOAD("allsport.3b", 0x08000, 0x4000, 0x7415a7fc);
            ROM_END();
        }
    };

    static RomLoadPtr rom_triviayp = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("ab01.bin", 0x10000, 0x4000, 0x97d35a85);
            ROM_LOAD("ab23.bin", 0x14000, 0x4000, 0x2ff67c70);
            ROM_LOAD("ab45.bin", 0x18000, 0x4000, 0x511a0fab);
            ROM_LOAD("ab67.bin", 0x1c000, 0x4000, 0xdf99d00c);
            ROM_LOAD("cd45.bin", 0x28000, 0x4000, 0xac45809e);
            ROM_LOAD("cd6ef.bin", 0x2c000, 0x4000, 0xa008059f);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("gr01.bin", 0x00000, 0x4000, 0x6829de8e);
            ROM_LOAD("gr23.bin", 0x04000, 0x4000, 0x89398700);
            ROM_LOAD("gr45.bin", 0x08000, 0x4000, 0x1242033e);
            ROM_END();
        }
    };

    static RomLoadPtr rom_triviabb = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("ab01.bin", 0x10000, 0x4000, 0x1b7c439d);
            ROM_LOAD("ab23.bin", 0x14000, 0x4000, 0xe4f1e704);
            ROM_LOAD("ab45.bin", 0x18000, 0x4000, 0xdaa2d8bc);
            ROM_LOAD("ab67.bin", 0x1c000, 0x4000, 0x3622c4f1);
            ROM_LOAD("cd45.bin", 0x28000, 0x4000, 0x07fd88ff);
            ROM_LOAD("cd6ef.bin", 0x2c000, 0x4000, 0x2d03f241);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("gr01.bin", 0x00000, 0x4000, 0x6829de8e);
            ROM_LOAD("gr23.bin", 0x04000, 0x4000, 0x89398700);
            ROM_LOAD("gr45.bin", 0x08000, 0x4000, 0x92fb6fb1);
            ROM_END();
        }
    };

    static RomLoadPtr rom_gimeabrk = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("ab01.u8a", 0x10000, 0x4000, 0x18cc53db);
            ROM_LOAD("ab23.u7a", 0x14000, 0x4000, 0x6bd4190a);
            ROM_LOAD("ab45.u6a", 0x18000, 0x4000, 0x5dca4f33);
            ROM_LOAD("cd6ef.uia", 0x2c000, 0x4000, 0x5e2b3510);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("gr01.u6b", 0x00000, 0x4000, 0xe3cdc476);
            ROM_LOAD("gr23.u5b", 0x04000, 0x4000, 0x0555d9c0);
            ROM_END();
        }
    };

    static RomLoadPtr rom_minigolf = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("ab01.u8a", 0x10000, 0x4000, 0x348f827f);
            ROM_LOAD("ab23.u7a", 0x14000, 0x4000, 0x19a6ff47);
            ROM_LOAD("ab45.u6a", 0x18000, 0x4000, 0x925d76eb);
            ROM_LOAD("ab67.u5a", 0x1c000, 0x4000, 0x6a311c9a);
            ROM_LOAD("1a-ver2", 0x20000, 0x10000, 0x60b6cd58);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("gr01.u6b", 0x00000, 0x4000, 0x8e24d594);
            ROM_LOAD("gr23.u5b", 0x04000, 0x4000, 0x3bf355ef);
            ROM_LOAD("gr45.u4b", 0x08000, 0x4000, 0x8eb14921);
            ROM_END();
        }
    };

    static RomLoadPtr rom_minigol2 = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("ab01.u8a", 0x10000, 0x4000, 0x348f827f);
            ROM_LOAD("ab23.u7a", 0x14000, 0x4000, 0x19a6ff47);
            ROM_LOAD("ab45.u6a", 0x18000, 0x4000, 0x925d76eb);
            ROM_LOAD("ab67.u5a", 0x1c000, 0x4000, 0x6a311c9a);
            ROM_LOAD("cd23.u3a", 0x24000, 0x4000, 0x52279801);
            ROM_LOAD("cd6ef.u1a", 0x2c000, 0x4000, 0x34c64f4c);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("gr01.u6b", 0x00000, 0x4000, 0x8e24d594);
            ROM_LOAD("gr23.u5b", 0x04000, 0x4000, 0x3bf355ef);
            ROM_LOAD("gr45.u4b", 0x08000, 0x4000, 0x8eb14921);
            ROM_END();
        }
    };

    static RomLoadPtr rom_toggle = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("tgle-ab0.bin", 0x10000, 0x2000, 0x8c7b7fad);
            ROM_LOAD("tgle-ab1.bin", 0x12000, 0x2000, 0x771e5434);
            ROM_LOAD("tgle-ab2.bin", 0x14000, 0x2000, 0x9b4baa3f);
            ROM_LOAD("tgle-ab3.bin", 0x16000, 0x2000, 0x35308a41);
            ROM_LOAD("tgle-ab4.bin", 0x18000, 0x2000, 0xbaf5617b);
            ROM_LOAD("tgle-ab5.bin", 0x1a000, 0x2000, 0x88077dad);
            ROM_LOAD("tgle-cd.bin", 0x2c000, 0x2000, 0x0a2bb949);
            ROM_LOAD("tgle-ef.bin", 0x2e000, 0x2000, 0x3ec10804);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("tgle-gr0.bin", 0x00000, 0x2000, 0x0e0e5d0e);
            ROM_LOAD("tgle-gr1.bin", 0x02000, 0x2000, 0x3b141ad2);
            ROM_END();
        }
    };

    static RomLoadPtr rom_nametune = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x70000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("nttab01.bin", 0x10000, 0x4000, 0xf99054f1);
            ROM_CONTINUE(0x40000, 0x4000);
            ROM_LOAD("nttab23.bin", 0x14000, 0x4000, 0xf2b8f7fa);
            ROM_CONTINUE(0x44000, 0x4000);
            ROM_LOAD("nttab45.bin", 0x18000, 0x4000, 0x89e1c769);
            ROM_CONTINUE(0x48000, 0x4000);
            ROM_LOAD("nttab67.bin", 0x1c000, 0x4000, 0x7e5572a1);
            ROM_CONTINUE(0x4c000, 0x4000);
            ROM_LOAD("nttcd01.bin", 0x20000, 0x4000, 0xdb9d6154);
            ROM_CONTINUE(0x50000, 0x4000);
            ROM_LOAD("nttcd23.bin", 0x24000, 0x4000, 0x9d2e458f);
            ROM_CONTINUE(0x54000, 0x4000);
            ROM_LOAD("nttcd45.bin", 0x28000, 0x4000, 0x9a4b87aa);
            ROM_CONTINUE(0x58000, 0x4000);
            ROM_LOAD("nttcd6ef.bin", 0x2c000, 0x4000, 0x0459e6f8);
            ROM_CONTINUE(0x5c000, 0x4000);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("nttgr0.bin", 0x00000, 0x8000, 0x6b75bb4b);
            ROM_END();
        }
    };

    static RomLoadPtr rom_nstocker = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("ab01.u8a", 0x10000, 0x4000, 0xa635f973);
            ROM_LOAD("ab23.u7a", 0x14000, 0x4000, 0x223acbb2);
            ROM_LOAD("ab45.u6a", 0x18000, 0x4000, 0x27a728b5);
            ROM_LOAD("ab67.u5a", 0x1c000, 0x4000, 0x2999cdf2);
            ROM_LOAD("cd01.u4a", 0x20000, 0x4000, 0x75e9b51a);
            ROM_LOAD("cd23.u3a", 0x24000, 0x4000, 0x0a32e0a5);
            ROM_LOAD("cd45.u2a", 0x28000, 0x4000, 0x9bb292fe);
            ROM_LOAD("cd6ef.u1a", 0x2c000, 0x4000, 0xe77c1aea);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("gr01.u4c", 0x00000, 0x4000, 0xfd0c38be);
            ROM_LOAD("gr23.u3c", 0x04000, 0x4000, 0x35d4433e);
            ROM_LOAD("gr45.u2c", 0x08000, 0x4000, 0x734b858a);
            ROM_LOAD("gr67.u1c", 0x0c000, 0x4000, 0x3311f9c0);
            ROM_END();
        }
    };

    static RomLoadPtr rom_sfootbal = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("sfbab01.bin", 0x10000, 0x4000, 0x2a69803f);
            ROM_LOAD("sfbab23.bin", 0x14000, 0x4000, 0x89f157c2);
            ROM_LOAD("sfbab45.bin", 0x18000, 0x4000, 0x91ad42c5);
            ROM_LOAD("sfbcd6ef.bin", 0x2c000, 0x4000, 0xbf80bb1a);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("sfbgr01.bin", 0x00000, 0x4000, 0xe3108d35);
            ROM_LOAD("sfbgr23.bin", 0x04000, 0x4000, 0x5c5af726);
            ROM_LOAD("sfbgr45.bin", 0x08000, 0x4000, 0xe767251e);
            ROM_LOAD("sfbgr67.bin", 0x0c000, 0x4000, 0x42452a7a);
            ROM_END();
        }
    };

    static RomLoadPtr rom_spiker = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("ab01.u8a", 0x10000, 0x4000, 0x2d53d023);
            ROM_LOAD("ab23.u7a", 0x14000, 0x4000, 0x3be87edf);
            ROM_LOAD("cd6ef.u1a", 0x2c000, 0x4000, 0xf2c73ece);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("gr01.u4c", 0x00000, 0x4000, 0x0caa6e3e);
            ROM_LOAD("gr23.u3c", 0x04000, 0x4000, 0x970c81f6);
            ROM_LOAD("gr45.u2c", 0x08000, 0x4000, 0x90ddd737);
            ROM_END();
        }
    };

    static RomLoadPtr rom_stompin = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("ab01.bin", 0x10000, 0x4000, 0x46f428c6);
            ROM_LOAD("ab23.bin", 0x14000, 0x4000, 0x0e13132f);
            ROM_LOAD("ab45.bin", 0x18000, 0x4000, 0x6ed26069);
            ROM_LOAD("ab67.bin", 0x1c000, 0x4000, 0x7f63b516);
            ROM_LOAD("cd23.bin", 0x24000, 0x4000, 0x52b29048);
            ROM_LOAD("cd6ef.bin", 0x2c000, 0x4000, 0xb880961a);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("gr01.u4c", 0x00000, 0x4000, 0x14ffdd1e);
            ROM_LOAD("gr23.u3c", 0x04000, 0x4000, 0x761abb80);
            ROM_LOAD("gr45.u2c", 0x08000, 0x4000, 0x0d2cf2e6);
            ROM_LOAD("gr67.u2c", 0x0c000, 0x4000, 0x2bab2784);
            ROM_END();
        }
    };

    static RomLoadPtr rom_rescraid = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("ab1.a10", 0x10000, 0x8000, 0x33a76b47);
            ROM_LOAD("ab12.a12", 0x18000, 0x8000, 0x7c7a9f12);
            ROM_LOAD("cd8.a16", 0x20000, 0x8000, 0x90917a43);
            ROM_LOAD("cd12.a18", 0x28000, 0x8000, 0x0450e9d7);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("gr0.a5", 0x00000, 0x8000, 0xe0dfc133);
            ROM_LOAD("gr4.a7", 0x08000, 0x8000, 0x952ade30);
            ROM_END();
        }
    };

    static RomLoadPtr rom_rescrdsa = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x40000, REGION_CPU1, 0);
            /* 64k for code for the first CPU, plus 128k of banked ROMs */
            ROM_LOAD("ab1-sa.a10", 0x10000, 0x8000, 0xaa0a9f48);
            ROM_LOAD("ab12-sa.a12", 0x18000, 0x8000, 0x16d4da86);
            ROM_LOAD("cd8-sa.a16", 0x20000, 0x8000, 0x9dfb50c2);
            ROM_LOAD("cd12-sa.a18", 0x28000, 0x8000, 0x18c62613);

            ROM_REGION(0x10000, REGION_CPU2, 0);
            /* 64k for Z80 */
            ROM_LOAD("sentesnd", 0x00000, 0x2000, 0x4dd0a525);

            ROM_REGION(0x10000, REGION_GFX1, 0);
            /* up to 64k of sprites */
            ROM_LOAD("gr0.a5", 0x00000, 0x8000, 0xe0dfc133);
            ROM_LOAD("gr4.a7", 0x08000, 0x8000, 0x952ade30);
            ROM_END();
        }
    };

    /**
     * ***********************************
     *
     * Game drivers
     *
     ************************************
     */
    public static GameDriver driver_sentetst = new GameDriver("1984", "sentetst", "balsente.java", rom_sentetst, null, machine_driver_balsente, input_ports_sentetst, init_sentetst, ROT0, "Bally/Sente", "Sente Diagnostic Cartridge");
    public static GameDriver driver_cshift = new GameDriver("1984", "cshift", "balsente.java", rom_cshift, null, machine_driver_balsente, input_ports_cshift, init_cshift, ROT0, "Bally/Sente", "Chicken Shift");
    public static GameDriver driver_gghost = new GameDriver("1984", "gghost", "balsente.java", rom_gghost, null, machine_driver_balsente, input_ports_gghost, init_gghost, ROT0, "Bally/Sente", "Goalie Ghost");
    public static GameDriver driver_hattrick = new GameDriver("1984", "hattrick", "balsente.java", rom_hattrick, null, machine_driver_balsente, input_ports_hattrick, init_hattrick, ROT0, "Bally/Sente", "Hat Trick");
    public static GameDriver driver_otwalls = new GameDriver("1984", "otwalls", "balsente.java", rom_otwalls, null, machine_driver_balsente, input_ports_otwalls, init_otwalls, ROT0, "Bally/Sente", "Off the Wall (Sente)");
    public static GameDriver driver_snakepit = new GameDriver("1984", "snakepit", "balsente.java", rom_snakepit, null, machine_driver_balsente, input_ports_snakepit, init_snakepit, ROT0, "Bally/Sente", "Snake Pit");
    public static GameDriver driver_snakjack = new GameDriver("1984", "snakjack", "balsente.java", rom_snakjack, null, machine_driver_balsente, input_ports_snakjack, init_snakjack, ROT0, "Bally/Sente", "Snacks'n Jaxson");
    public static GameDriver driver_stocker = new GameDriver("1984", "stocker", "balsente.java", rom_stocker, null, machine_driver_balsente, input_ports_stocker, init_stocker, ROT0, "Bally/Sente", "Stocker");
    public static GameDriver driver_triviag1 = new GameDriver("1984", "triviag1", "balsente.java", rom_triviag1, null, machine_driver_balsente, input_ports_triviag1, init_triviag1, ROT0, "Bally/Sente", "Trivial Pursuit (Genus I)");
    public static GameDriver driver_triviag2 = new GameDriver("1984", "triviag2", "balsente.java", rom_triviag2, null, machine_driver_balsente, input_ports_triviag2, init_triviag2, ROT0, "Bally/Sente", "Trivial Pursuit (Genus II)");
    public static GameDriver driver_triviasp = new GameDriver("1984", "triviasp", "balsente.java", rom_triviasp, null, machine_driver_balsente, input_ports_triviasp, init_triviag2, ROT0, "Bally/Sente", "Trivial Pursuit (All Star Sports Edition)");
    public static GameDriver driver_triviayp = new GameDriver("1984", "triviayp", "balsente.java", rom_triviayp, null, machine_driver_balsente, input_ports_triviayp, init_triviag2, ROT0, "Bally/Sente", "Trivial Pursuit (Young Players Edition)");
    public static GameDriver driver_triviabb = new GameDriver("1984", "triviabb", "balsente.java", rom_triviabb, null, machine_driver_balsente, input_ports_triviabb, init_triviag2, ROT0, "Bally/Sente", "Trivial Pursuit (Baby Boomer Edition)");
    public static GameDriver driver_gimeabrk = new GameDriver("1985", "gimeabrk", "balsente.java", rom_gimeabrk, null, machine_driver_balsente, input_ports_gimeabrk, init_gimeabrk, ROT0, "Bally/Sente", "Gimme A Break");
    public static GameDriver driver_minigolf = new GameDriver("1985", "minigolf", "balsente.java", rom_minigolf, null, machine_driver_balsente, input_ports_minigolf, init_minigolf, ROT0, "Bally/Sente", "Mini Golf (set 1)");
    public static GameDriver driver_minigol2 = new GameDriver("1985", "minigol2", "balsente.java", rom_minigol2, driver_minigolf, machine_driver_balsente, input_ports_minigol2, init_minigol2, ROT0, "Bally/Sente", "Mini Golf (set 2)");
    public static GameDriver driver_toggle = new GameDriver("1985", "toggle", "balsente.java", rom_toggle, null, machine_driver_balsente, input_ports_toggle, init_toggle, ROT0, "Bally/Sente", "Toggle");
    public static GameDriver driver_nametune = new GameDriver("1986", "nametune", "balsente.java", rom_nametune, null, machine_driver_balsente, input_ports_nametune, init_nametune, ROT0, "Bally/Sente", "Name That Tune");
    public static GameDriver driver_nstocker = new GameDriver("1986", "nstocker", "balsente.java", rom_nstocker, null, machine_driver_balsente, input_ports_nstocker, init_nstocker, ROT0, "Bally/Sente", "Night Stocker");
    public static GameDriver driver_sfootbal = new GameDriver("1986", "sfootbal", "balsente.java", rom_sfootbal, null, machine_driver_balsente, input_ports_sfootbal, init_sfootbal, ROT0, "Bally/Sente", "Street Football");
    public static GameDriver driver_spiker = new GameDriver("1986", "spiker", "balsente.java", rom_spiker, null, machine_driver_balsente, input_ports_spiker, init_spiker, ROT0, "Bally/Sente", "Spiker");
    public static GameDriver driver_stompin = new GameDriver("1986", "stompin", "balsente.java", rom_stompin, null, machine_driver_balsente, input_ports_stompin, init_stompin, ROT0, "Bally/Sente", "Stompin'");
    public static GameDriver driver_rescraid = new GameDriver("1987", "rescraid", "balsente.java", rom_rescraid, null, machine_driver_balsente, input_ports_rescraid, init_rescraid, ROT0, "Bally/Sente", "Rescue Raider");
    public static GameDriver driver_rescrdsa = new GameDriver("1987", "rescrdsa", "balsente.java", rom_rescrdsa, driver_rescraid, machine_driver_balsente, input_ports_rescraid, init_rescraid, ROT0, "Bally/Sente", "Rescue Raider (Stand-Alone)");
    
    // more games (from mame 0.104u7)
    public static GameDriver driver_triviaes = new GameDriver("1987", "triviaes", "balsente.java", rom_triviaes, null, machine_driver_balsente, input_ports_triviaes, init_triviaes, ROT0, "Bally/Sente (Maibesa license)",  "Trivial Pursuit (Spanish Edition)");
}
