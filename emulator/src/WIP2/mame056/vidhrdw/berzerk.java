/***************************************************************************

  vidhrdw.c

  Functions to emulate the video hardware of the machine.

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP2.mame056.vidhrdw;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.libc.cstring.memset;

import static WIP2.common.ptr.*;
import WIP2.common.subArrays.IntArray;
import static WIP2.mame056.common.*;

import static WIP2.mame056.palette.*;
import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.drawgfxH.*;

import static WIP2.mame056.vidhrdw.generic.*;

// refactor
import static WIP2.arcadeflex036.osdepend.logerror;

import static WIP2.mame056.machine.berzerk.berzerk_irq_end_of_screen;

public class berzerk {

    public static UBytePtr berzerk_magicram = new UBytePtr();

    static int magicram_control = 0xff;
    static int magicram_latch = 0xff;
    static int collision = 0;

    public static void copy_byte(int x, int y, int data, int col) {
        char fore, back;

        if (y < 32) {
            return;
        }

        fore = (char)Machine.pens.read((col >> 4) & 0x0f);
        back = (char)Machine.pens.read(0);

        plot_pixel.handler(Machine.scrbitmap, x, y, (data & 0x80) != 0 ? fore : back);
        plot_pixel.handler(Machine.scrbitmap, x + 1, y, (data & 0x40) != 0 ? fore : back);
        plot_pixel.handler(Machine.scrbitmap, x + 2, y, (data & 0x20) != 0 ? fore : back);
        plot_pixel.handler(Machine.scrbitmap, x + 3, y, (data & 0x10) != 0 ? fore : back);

        fore = (char)Machine.pens.read(col & 0x0f);

        plot_pixel.handler(Machine.scrbitmap, x + 4, y, (data & 0x08) != 0 ? fore : back);
        plot_pixel.handler(Machine.scrbitmap, x + 5, y, (data & 0x04) != 0 ? fore : back);
        plot_pixel.handler(Machine.scrbitmap, x + 6, y, (data & 0x02) != 0 ? fore : back);
        plot_pixel.handler(Machine.scrbitmap, x + 7, y, (data & 0x01) != 0 ? fore : back);
    }

    public static WriteHandlerPtr berzerk_videoram_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            int coloroffset, x, y;

            videoram.write(offset, data);

            /* Get location of color RAM for this offset */
            coloroffset = ((offset & 0xff80) >> 2) | (offset & 0x1f);

            y = (offset >> 5);
            x = (offset & 0x1f) << 3;

            copy_byte(x, y, data, colorram.read(coloroffset));
        }
    };

    public static WriteHandlerPtr berzerk_colorram_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            int x, y, i;

            colorram.write(offset, data);

            /* Need to change the affected pixels' colors */
            y = ((offset >> 3) & 0xfc);
            x = (offset & 0x1f) << 3;

            for (i = 0; i < 4; i++, y++) {
                int _byte = videoram.read((y << 5) | (x >> 3));

                if (_byte != 0) {
                    copy_byte(x, y, _byte, data);
                }
            }
        }
    };

    public static /*unsigned*/ char reverse_byte(/*unsigned*/char data) {
        return (char) ((((data & 0x01) << 7)
                | ((data & 0x02) << 5)
                | ((data & 0x04) << 3)
                | ((data & 0x08) << 1)
                | ((data & 0x10) >> 1)
                | ((data & 0x20) >> 3)
                | ((data & 0x40) >> 5)
                | ((data & 0x80) >> 7)) & 0xFF);
    }

    public static int shifter_flopper(/*unsigned*/char data) {
        int shift_amount, outval;

        /* Bits 0-2 are the shift amount */
        shift_amount = magicram_control & 0x06;

        outval = ((data >> shift_amount) | (magicram_latch << (8 - shift_amount))) & 0x1ff;
        outval >>= (magicram_control & 0x01);

        /* Bit 3 is the flip bit */
        if ((magicram_control & 0x08) != 0) {
            outval = reverse_byte((char) ((outval) & 0xFF));
        }

        return outval;
    }

    public static WriteHandlerPtr berzerk_magicram_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            int data2;

            data2 = shifter_flopper((char) ((data) & 0xFF));

            magicram_latch = data;

            /* Check for collision */
            if (collision == 0) {
                collision = (data2 & videoram.read(offset));
            }

            switch (magicram_control & 0xf0) {
                case 0x00:
                    berzerk_magicram.write(offset, data2);
                    break;
                case 0x10:
                    berzerk_magicram.write(offset, data2 | videoram.read(offset));
                    break;
                case 0x20:
                    berzerk_magicram.write(offset, data2 | ~videoram.read(offset));
                    break;
                case 0x30:
                    berzerk_magicram.write(offset, 0xff);
                    break;
                case 0x40:
                    berzerk_magicram.write(offset, data2 & videoram.read(offset));
                    break;
                case 0x50:
                    berzerk_magicram.write(offset, videoram.read(offset));
                    break;
                case 0x60:
                    berzerk_magicram.write(offset, ~(data2 ^ videoram.read(offset)));
                    break;
                case 0x70:
                    berzerk_magicram.write(offset, ~data2 | videoram.read(offset));
                    break;
                case 0x80:
                    berzerk_magicram.write(offset, data2 & ~videoram.read(offset));
                    break;
                case 0x90:
                    berzerk_magicram.write(offset, data2 ^ videoram.read(offset));
                    break;
                case 0xa0:
                    berzerk_magicram.write(offset, ~videoram.read(offset));
                    break;
                case 0xb0:
                    berzerk_magicram.write(offset, ~(data2 & videoram.read(offset)));
                    break;
                case 0xc0:
                    berzerk_magicram.write(offset, 0x00);
                    break;
                case 0xd0:
                    berzerk_magicram.write(offset, ~data2 & videoram.read(offset));
                    break;
                case 0xe0:
                    berzerk_magicram.write(offset, ~(data2 | videoram.read(offset)));
                    break;
                case 0xf0:
                    berzerk_magicram.write(offset, ~data2);
                    break;
            }

            berzerk_videoram_w.handler(offset, berzerk_magicram.read(offset));
        }
    };

    public static WriteHandlerPtr berzerk_magicram_control_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            magicram_control = data;
            magicram_latch = 0;
            collision = 0;
        }
    };

    public static ReadHandlerPtr berzerk_collision_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            int ret = (collision != 0 ? 0x80 : 0x00);

            return ret | berzerk_irq_end_of_screen;
        }
    };

    /**
     * *************************************************************************
     *
     * Draw the game screen in the given mame_bitmap. To be used by bitmapped
     * games not using sprites.
     *
     **************************************************************************
     */
    public static VhUpdatePtr berzerk_vh_screenrefresh = new VhUpdatePtr() {
        public void handler(mame_bitmap bitmap, int full_refresh) {
            if (full_refresh != 0) {
                /* redraw bitmap */

                int offs;

                for (offs = 0; offs < videoram_size[0]; offs++) {
                    berzerk_videoram_w.handler(offs, videoram.read(offs));
                }
            }
        }
    };
}
