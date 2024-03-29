/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */
package WIP.mame056.drivers;

import static WIP2.arcadeflex056.fucPtr.*;
import WIP2.common.ptr.UBytePtr;

import static WIP2.mame056.common.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.inptportH.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.driverH.*;
import static WIP2.mame056.memoryH.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.inputH.*;
import static WIP2.mame056.sndintrfH.*;
import static WIP2.mame056.sndintrf.*;
import static WIP2.mame056.sound.samples.*;
import static WIP2.mame056.sound.samplesH.*;
import static WIP2.mame056.cpu.m6800.hd63701.*;
import static WIP2.mame056.cpu.m6800.m6800H.*;

import static WIP2.mame056.vidhrdw.generic.*;
import static WIP2.mame056.mame.*;
import static WIP2.arcadeflex056.fileio.*;
import static mame056.palette.*;

import static WIP.mame056.vidhrdw.pacland.*;
import static WIP2.mame056.machine.mappy.*;
import static WIP2.mame056.sound.namco.*;
import static WIP2.mame056.sound.namcoH.*;

public class pacland {

    static UBytePtr sharedram1 = new UBytePtr();

    public static ReadHandlerPtr sharedram1_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            return sharedram1.read(offset);
        }
    };

    public static WriteHandlerPtr sharedram1_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            sharedram1.write(offset, data);
        }
    };

    public static WriteHandlerPtr pacland_halt_mcu_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            if (offset == 0) {
                cpu_set_reset_line(1, CLEAR_LINE);
            } else {
                cpu_set_reset_line(1, ASSERT_LINE);
            }
        }
    };

    /* Stubs to pass the correct Dip Switch setup to the MCU */
    public static ReadHandlerPtr dsw0_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            /* Hi 4 bits = DSWA Hi 4 bits */
 /* Lo 4 bits = DSWB Hi 4 bits */
            int r = readinputport(0);
            r &= 0xf0;
            r |= (readinputport(1) >> 4) & 0x0f;
            return ~r;
            /* Active Low */
        }
    };

    public static ReadHandlerPtr dsw1_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            /* Hi 4 bits = DSWA Lo 4 bits */
 /* Lo 4 bits = DSWB Lo 4 bits */
            int r = (readinputport(0) & 0x0f) << 4;
            r |= readinputport(1) & 0x0f;
            return ~r;
            /* Active Low */
        }
    };

    public static WriteHandlerPtr pacland_coin_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            coin_lockout_global_w(data & 1);
            coin_counter_w.handler(0, ~data & 2);
            coin_counter_w.handler(1, ~data & 4);
        }
    };

    public static WriteHandlerPtr pacland_led_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            set_led_status(0, data & 0x08);
            set_led_status(1, data & 0x10);
        }
    };

    public static Memory_ReadAddress readmem[] = {
        new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
        new Memory_ReadAddress(0x0000, 0x1fff, videoram_r),
        new Memory_ReadAddress(0x2000, 0x37ff, MRA_RAM),
        new Memory_ReadAddress(0x4000, 0x5fff, MRA_BANK1),
        new Memory_ReadAddress(0x6800, 0x68ff, namcos1_wavedata_r), /* PSG device, shared RAM */
        new Memory_ReadAddress(0x6800, 0x6bff, sharedram1_r),
        new Memory_ReadAddress(0x7800, 0x7800, MRA_NOP), /* ??? */
        new Memory_ReadAddress(0x8000, 0xffff, MRA_ROM),
        new Memory_ReadAddress(MEMPORT_MARKER, 0)
    };

    public static Memory_WriteAddress writemem[] = {
        new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
        new Memory_WriteAddress(0x0000, 0x2000, videoram_w, videoram, videoram_size),
        new Memory_WriteAddress(0x2000, 0x37ff, MWA_RAM),
        new Memory_WriteAddress(0x2700, 0x27ff, MWA_RAM, spriteram, spriteram_size),
        new Memory_WriteAddress(0x2f00, 0x2fff, MWA_RAM, spriteram_2),
        new Memory_WriteAddress(0x3700, 0x37ff, MWA_RAM, spriteram_3),
        new Memory_WriteAddress(0x3800, 0x3801, pacland_scroll0_w),
        new Memory_WriteAddress(0x3a00, 0x3a01, pacland_scroll1_w),
        new Memory_WriteAddress(0x3c00, 0x3c00, pacland_bankswitch_w),
        new Memory_WriteAddress(0x4000, 0x5fff, MWA_ROM),
        new Memory_WriteAddress(0x6800, 0x68ff, namcos1_wavedata_w), /* PSG device, shared RAM */
        new Memory_WriteAddress(0x6800, 0x6bff, sharedram1_w, sharedram1),
        new Memory_WriteAddress(0x7000, 0x7000, MWA_NOP), /* ??? */
        new Memory_WriteAddress(0x7800, 0x7800, MWA_NOP), /* ??? */
        new Memory_WriteAddress(0x8000, 0x8800, pacland_halt_mcu_w),
        new Memory_WriteAddress(0x9800, 0x9800, MWA_NOP), /* ??? */
        new Memory_WriteAddress(0x8000, 0xffff, MWA_ROM),
        new Memory_WriteAddress(MEMPORT_MARKER, 0)
    };

    public static Memory_ReadAddress mcu_readmem[] = {
        new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
        new Memory_ReadAddress(0x0000, 0x001f, hd63701_internal_registers_r),
        new Memory_ReadAddress(0x0080, 0x00ff, MRA_RAM),
        new Memory_ReadAddress(0x1000, 0x10ff, namcos1_wavedata_r), /* PSG device, shared RAM */
        new Memory_ReadAddress(0x1100, 0x113f, MRA_RAM), /* PSG device */
        new Memory_ReadAddress(0x1000, 0x13ff, sharedram1_r),
        new Memory_ReadAddress(0x8000, 0x9fff, MRA_ROM),
        new Memory_ReadAddress(0xc000, 0xc800, MRA_RAM),
        new Memory_ReadAddress(0xd000, 0xd000, dsw0_r),
        new Memory_ReadAddress(0xd000, 0xd001, dsw1_r),
        new Memory_ReadAddress(0xd000, 0xd002, input_port_2_r),
        new Memory_ReadAddress(0xd000, 0xd003, input_port_3_r),
        new Memory_ReadAddress(0xf000, 0xffff, MRA_ROM),
        new Memory_ReadAddress(MEMPORT_MARKER, 0)
    };

    public static Memory_WriteAddress mcu_writemem[] = {
        new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
        new Memory_WriteAddress(0x0000, 0x001f, hd63701_internal_registers_w),
        new Memory_WriteAddress(0x0080, 0x00ff, MWA_RAM),
        new Memory_WriteAddress(0x1000, 0x10ff, namcos1_wavedata_w, namco_wavedata), /* PSG device, shared RAM */
        new Memory_WriteAddress(0x1100, 0x113f, namcos1_sound_w, namco_soundregs), /* PSG device */
        new Memory_WriteAddress(0x1000, 0x13ff, sharedram1_w),
        new Memory_WriteAddress(0x2000, 0x2000, MWA_NOP), // ???? (w)
        new Memory_WriteAddress(0x4000, 0x4000, MWA_NOP), // ???? (w)
        new Memory_WriteAddress(0x6000, 0x6000, MWA_NOP), // ???? (w)
        new Memory_WriteAddress(0x8000, 0x9fff, MWA_ROM),
        new Memory_WriteAddress(0xc000, 0xc7ff, MWA_RAM),
        new Memory_WriteAddress(0xf000, 0xffff, MWA_ROM),
        new Memory_WriteAddress(MEMPORT_MARKER, 0)
    };

    public static ReadHandlerPtr readFF = new ReadHandlerPtr() {
        public int handler(int offset) {
            return 0xff;
        }
    };

    public static IO_ReadPort mcu_readport[] = {
        new IO_ReadPort(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),
        new IO_ReadPort(HD63701_PORT1, HD63701_PORT1, input_port_4_r),
        new IO_ReadPort(HD63701_PORT2, HD63701_PORT2, readFF), /* leds won't work otherwise */
        new IO_ReadPort(MEMPORT_MARKER, 0)
    };

    public static IO_WritePort mcu_writeport[] = {
        new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),
        new IO_WritePort(HD63701_PORT1, HD63701_PORT1, pacland_coin_w),
        new IO_WritePort(HD63701_PORT2, HD63701_PORT2, pacland_led_w),
        new IO_WritePort(MEMPORT_MARKER, 0)
    };

    static InputPortPtr input_ports_pacland = new InputPortPtr() {
        public void handler() {
            PORT_START();
            /* DSWA */
            PORT_DIPNAME(0x03, 0x00, DEF_STR("Coin_B"));
            PORT_DIPSETTING(0x03, DEF_STR("3C_1C"));
            PORT_DIPSETTING(0x02, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x00, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x01, DEF_STR("1C_2C"));
            PORT_DIPNAME(0x04, 0x00, DEF_STR("Demo_Sounds"));
            PORT_DIPSETTING(0x04, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x18, 0x00, DEF_STR("Coin_A"));
            PORT_DIPSETTING(0x18, DEF_STR("3C_1C"));
            PORT_DIPSETTING(0x10, DEF_STR("2C_1C"));
            PORT_DIPSETTING(0x00, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x08, DEF_STR("1C_2C"));
            PORT_DIPNAME(0x60, 0x00, DEF_STR("Lives"));
            PORT_DIPSETTING(0x20, "2");
            PORT_DIPSETTING(0x00, "3");
            PORT_DIPSETTING(0x40, "4");
            PORT_DIPSETTING(0x60, "5");
            PORT_DIPNAME(0x80, 0x00, "Test Mode");
            PORT_DIPSETTING(0x00, DEF_STR("Off"));
            PORT_DIPSETTING(0x80, DEF_STR("On"));

            PORT_START();
            /* DSWB */
            PORT_DIPNAME(0x01, 0x00, "Start Level Select");
            PORT_DIPSETTING(0x01, DEF_STR("Off"));
            PORT_DIPSETTING(0x00, DEF_STR("On"));
            PORT_DIPNAME(0x02, 0x00, "Freeze");
            PORT_DIPSETTING(0x00, DEF_STR("Off"));
            PORT_DIPSETTING(0x02, DEF_STR("On"));
            PORT_BITX(0x04, 0x00, IPT_DIPSWITCH_NAME | IPF_CHEAT, "Round Select", IP_KEY_NONE, IP_JOY_NONE);
            PORT_DIPSETTING(0x00, DEF_STR("Off"));
            PORT_DIPSETTING(0x04, DEF_STR("On"));
            PORT_DIPNAME(0x18, 0x00, DEF_STR("Difficulty"));
            PORT_DIPSETTING(0x08, "Easy");
            PORT_DIPSETTING(0x00, "Medium");
            PORT_DIPSETTING(0x10, "Hard");
            PORT_DIPSETTING(0x18, "Hardest");
            PORT_DIPNAME(0xe0, 0x00, DEF_STR("Bonus_Life"));
            PORT_DIPSETTING(0x00, "30K,80K,130K,300K,500K,1M");
            PORT_DIPSETTING(0x20, "30K,100K,200K,400K,600K,1M");
            PORT_DIPSETTING(0x40, "40K,100K,180K,300K,500K,1M");
            PORT_DIPSETTING(0x60, "30K,80K,Every 100K");
            PORT_DIPSETTING(0x80, "50K,150K,Every 200K");
            PORT_DIPSETTING(0xa0, "30K,80K,150K");
            PORT_DIPSETTING(0xc0, "40K,100K,200K");
            PORT_DIPSETTING(0xe0, "40K");

            PORT_START();
            /* Memory Mapped Port */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_UP);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_COCKTAIL);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_COCKTAIL);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_START1);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_START2);
            PORT_SERVICE(0x40, IP_ACTIVE_LOW);
            PORT_DIPNAME(0x80, 0x80, DEF_STR("Cabinet"));
            PORT_DIPSETTING(0x80, DEF_STR("Upright"));
            PORT_DIPSETTING(0x00, DEF_STR("Cocktail"));

            PORT_START();
            /* Memory Mapped Port */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_UNUSED);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_COCKTAIL);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_SERVICE1);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_COIN1);
            PORT_BIT(0x80, IP_ACTIVE_LOW, IPT_COIN2);

            PORT_START();
            /* MCU Input Port */
            PORT_BIT(0x01, IP_ACTIVE_LOW, IPT_SPECIAL);/* OUT:coin lockout */
            PORT_BIT(0x02, IP_ACTIVE_LOW, IPT_SPECIAL);/* OUT:coin counter 1 */
            PORT_BIT(0x04, IP_ACTIVE_LOW, IPT_SPECIAL);/* OUT:coin counter 2 */
            PORT_BIT(0x08, IP_ACTIVE_LOW, IPT_BUTTON1);
            PORT_BIT(0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT);
            PORT_BIT(0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT);
            PORT_BIT(0x40, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL);
            PORT_BIT(0x80, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_COCKTAIL);
            INPUT_PORTS_END();
        }
    };

    static GfxLayout spritelayout = new GfxLayout(
            16, 16, /* 16*16 sprites */
            256, /* 256 sprites */
            4, /* 4 bits per pixel */
            new int[]{0, 4, 16384 * 8, 16384 * 8 + 4},
            new int[]{0, 1, 2, 3, 8 * 8, 8 * 8 + 1, 8 * 8 + 2, 8 * 8 + 3,
                16 * 8 + 0, 16 * 8 + 1, 16 * 8 + 2, 16 * 8 + 3, 24 * 8 + 0, 24 * 8 + 1, 24 * 8 + 2, 24 * 8 + 3},
            new int[]{0 * 8, 1 * 8, 2 * 8, 3 * 8, 4 * 8, 5 * 8, 6 * 8, 7 * 8,
                32 * 8, 33 * 8, 34 * 8, 35 * 8, 36 * 8, 37 * 8, 38 * 8, 39 * 8},
            64 * 8 /* every sprite takes 256 bytes */
    );

    static GfxLayout charlayout = new GfxLayout(
            8, 8, /* 8*8 characters */
            512, /* 512 characters */
            2, /* 2 bits per pixel */
            new int[]{0, 4}, /* the bitplanes are packed in the same byte */
            new int[]{8 * 8, 8 * 8 + 1, 8 * 8 + 2, 8 * 8 + 3, 0, 1, 2, 3},
            new int[]{0 * 8, 1 * 8, 2 * 8, 3 * 8, 4 * 8, 5 * 8, 6 * 8, 7 * 8},
            16 * 8 /* every char takes 16 consecutive bytes */
    );

    static GfxDecodeInfo gfxdecodeinfo[]
            = {
                new GfxDecodeInfo(REGION_GFX1, 0, charlayout, 0, 256),
                new GfxDecodeInfo(REGION_GFX2, 0, charlayout, 256 * 4, 256),
                new GfxDecodeInfo(REGION_GFX3, 0, spritelayout, 256 * 4 + 256 * 4, 3 * 64),
                new GfxDecodeInfo(REGION_GFX4, 0, spritelayout, 256 * 4 + 256 * 4, 3 * 64),
                new GfxDecodeInfo(-1) /* end of array */};

    static namco_interface namco_interface = new namco_interface(
            24000, /* sample rate */
            8, /* number of voices */
            100, /* playback volume */
            -1, /* memory region */
            0 /* stereo */
    );

    static MachineDriver machine_driver_pacland = new MachineDriver(
            /* basic machine hardware */
            new MachineCPU[]{
                new MachineCPU(
                        CPU_M6809,
                        1500000, /* 1.500 MHz (?) */
                        readmem, writemem, null, null,
                        interrupt, 1
                ),
                new MachineCPU(
                        CPU_HD63701, /* or compatible 6808 with extra instructions */
                        //			6000000/4,		/* ??? */
                        (int) (6000000 / 3.9), /* ??? */
                        mcu_readmem, mcu_writemem, mcu_readport, mcu_writeport,
                        interrupt, 1
                ),},
            60.606060f, DEFAULT_REAL_60HZ_VBLANK_DURATION,
            100, /* we need heavy synching between the MCU and the CPU */
            null,
            /* video hardware */
            42 * 8, 32 * 8, new rectangle(3 * 8, 39 * 8 - 1, 2 * 8, 30 * 8 - 1),
            gfxdecodeinfo,
            256, 256 * 4 + 256 * 4 + 3 * 64 * 16,
            pacland_vh_convert_color_prom,
            VIDEO_TYPE_RASTER | VIDEO_UPDATE_AFTER_VBLANK,
            null,
            pacland_vh_start,
            pacland_vh_stop,
            pacland_vh_screenrefresh,
            /* sound hardware */
            0, 0, 0, 0,
            new MachineSound[]{
                new MachineSound(
                        SOUND_NAMCO,
                        namco_interface
                )
            }
    );

    /**
     * *************************************************************************
     *
     * Game driver(s)
     *
     **************************************************************************
     */
    static RomLoadPtr rom_pacland = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x20000, REGION_CPU1, 0);/* 128k for code */
            ROM_LOAD("pl5_01b.bin", 0x08000, 0x4000, 0xb0ea7631);
            ROM_LOAD("pl5_02.bin", 0x0C000, 0x4000, 0xd903e84e);
            /* all the following are banked at 0x4000-0x5fff */
            ROM_LOAD("pl1-3", 0x10000, 0x4000, 0xaa9fa739);
            ROM_LOAD("pl1-4", 0x14000, 0x4000, 0x2b895a90);
            ROM_LOAD("pl1-5", 0x18000, 0x4000, 0x7af66200);
            ROM_LOAD("pl3_06.bin", 0x1c000, 0x4000, 0x2ffe3319);

            ROM_REGION(0x10000, REGION_CPU2, 0);/* 64k for code */
            ROM_LOAD("pl1-7", 0x8000, 0x2000, 0x8c5becae);/* sub program for the mcu */
            ROM_LOAD("pl1-mcu.bin", 0xf000, 0x1000, 0x6ef08fb3);/* microcontroller */

            ROM_REGION(0x02000, REGION_GFX1, ROMREGION_DISPOSE);
            ROM_LOAD("pl2_12.bin", 0x00000, 0x2000, 0xa63c8726);/* chars */

            ROM_REGION(0x02000, REGION_GFX2, ROMREGION_DISPOSE);
            ROM_LOAD("pl4_13.bin", 0x00000, 0x2000, 0x3ae582fd);

            ROM_REGION(0x08000, REGION_GFX3, ROMREGION_DISPOSE);
            ROM_LOAD("pl1-9", 0x00000, 0x4000, 0xf5d5962b);/* sprites */
            ROM_LOAD("pl1-10", 0x04000, 0x4000, 0xc7cf1904);

            ROM_REGION(0x08000, REGION_GFX4, ROMREGION_DISPOSE);
            ROM_LOAD("pl1-8", 0x00000, 0x4000, 0xa2ebfa4a);
            ROM_LOAD("pl1-11", 0x04000, 0x4000, 0x6621361a);

            ROM_REGION(0x1400, REGION_PROMS, 0);
            ROM_LOAD("pl1-2.bin", 0x0000, 0x0400, 0x472885de);/* red and green component */
            ROM_LOAD("pl1-1.bin", 0x0400, 0x0400, 0xa78ebdaf);/* blue component */
            ROM_LOAD("pl1-3.bin", 0x0800, 0x0400, 0x80558da8);/* sprites lookup table */
            ROM_LOAD("pl1-5.bin", 0x0c00, 0x0400, 0x4b7ee712);/* foreground lookup table */
            ROM_LOAD("pl1-4.bin", 0x1000, 0x0400, 0x3a7be418);/* background lookup table */
            ROM_END();
        }
    };

    static RomLoadPtr rom_pacland2 = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x20000, REGION_CPU1, 0);/* 128k for code */
            ROM_LOAD("pl6_01.bin", 0x08000, 0x4000, 0x4c96e11c);
            ROM_LOAD("pl6_02.bin", 0x0C000, 0x4000, 0x8cf5bd8d);
            /* all the following are banked at 0x4000-0x5fff */
            ROM_LOAD("pl1-3", 0x10000, 0x4000, 0xaa9fa739);
            ROM_LOAD("pl1-4", 0x14000, 0x4000, 0x2b895a90);
            ROM_LOAD("pl1-5", 0x18000, 0x4000, 0x7af66200);
            ROM_LOAD("pl1-6", 0x1c000, 0x4000, 0xb01e59a9);

            ROM_REGION(0x10000, REGION_CPU2, 0);/* 64k for code */
            ROM_LOAD("pl1-7", 0x8000, 0x2000, 0x8c5becae);/* sub program for the mcu */
            ROM_LOAD("pl1-mcu.bin", 0xf000, 0x1000, 0x6ef08fb3);/* microcontroller */

            ROM_REGION(0x02000, REGION_GFX1, ROMREGION_DISPOSE);
            ROM_LOAD("pl0_12.bin", 0x00000, 0x2000, 0xc8cb61ab);/* chars */

            ROM_REGION(0x02000, REGION_GFX2, ROMREGION_DISPOSE);
            ROM_LOAD("pl1-13", 0x00000, 0x2000, 0x6c5ed9ae);

            ROM_REGION(0x08000, REGION_GFX3, ROMREGION_DISPOSE);
            ROM_LOAD("pl1_09b.bin", 0x00000, 0x4000, 0x80768a87);/* sprites */
            ROM_LOAD("pl1_10b.bin", 0x04000, 0x4000, 0xffd9d66e);

            ROM_REGION(0x08000, REGION_GFX4, ROMREGION_DISPOSE);
            ROM_LOAD("pl1_08.bin", 0x00000, 0x4000, 0x2b20e46d);
            ROM_LOAD("pl1_11.bin", 0x04000, 0x4000, 0xc59775d8);

            ROM_REGION(0x1400, REGION_PROMS, 0);
            ROM_LOAD("pl1-2.bin", 0x0000, 0x0400, 0x472885de);/* red and green component */
            ROM_LOAD("pl1-1.bin", 0x0400, 0x0400, 0xa78ebdaf);/* blue component */
            ROM_LOAD("pl1-3.bin", 0x0800, 0x0400, 0x80558da8);/* sprites lookup table */
            ROM_LOAD("pl1-5.bin", 0x0c00, 0x0400, 0x4b7ee712);/* foreground lookup table */
            ROM_LOAD("pl1-4.bin", 0x1000, 0x0400, 0x3a7be418);/* background lookup table */
            ROM_END();
        }
    };

    static RomLoadPtr rom_pacland3 = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x20000, REGION_CPU1, 0);/* 128k for code */
            ROM_LOAD("pln1-1", 0x08000, 0x4000, 0xf729fb94);
            ROM_LOAD("pln1-2", 0x0C000, 0x4000, 0x5c66eb6f);
            /* all the following are banked at 0x4000-0x5fff */
            ROM_LOAD("pl1-3", 0x10000, 0x4000, 0xaa9fa739);
            ROM_LOAD("pl1-4", 0x14000, 0x4000, 0x2b895a90);
            ROM_LOAD("pl1-5", 0x18000, 0x4000, 0x7af66200);
            ROM_LOAD("pl1-6", 0x1c000, 0x4000, 0xb01e59a9);

            ROM_REGION(0x10000, REGION_CPU2, 0);/* 64k for code */
            ROM_LOAD("pl1-7", 0x8000, 0x2000, 0x8c5becae);/* sub program for the mcu */
            ROM_LOAD("pl1-mcu.bin", 0xf000, 0x1000, 0x6ef08fb3);/* microcontroller */

            ROM_REGION(0x02000, REGION_GFX1, ROMREGION_DISPOSE);
            ROM_LOAD("pl1-12", 0x00000, 0x2000, 0xc159fbce);/* chars */

            ROM_REGION(0x02000, REGION_GFX2, ROMREGION_DISPOSE);
            ROM_LOAD("pl1-13", 0x00000, 0x2000, 0x6c5ed9ae);

            ROM_REGION(0x08000, REGION_GFX3, ROMREGION_DISPOSE);
            ROM_LOAD("pl1_09b.bin", 0x00000, 0x4000, 0x80768a87);/* sprites */
            ROM_LOAD("pl1_10b.bin", 0x04000, 0x4000, 0xffd9d66e);

            ROM_REGION(0x08000, REGION_GFX4, ROMREGION_DISPOSE);
            ROM_LOAD("pl1_08.bin", 0x00000, 0x4000, 0x2b20e46d);
            ROM_LOAD("pl1_11.bin", 0x04000, 0x4000, 0xc59775d8);

            ROM_REGION(0x1400, REGION_PROMS, 0);
            ROM_LOAD("pl1-2.bin", 0x0000, 0x0400, 0x472885de);/* red and green component */
            ROM_LOAD("pl1-1.bin", 0x0400, 0x0400, 0xa78ebdaf);/* blue component */
            ROM_LOAD("pl1-3.bin", 0x0800, 0x0400, 0x80558da8);/* sprites lookup table */
            ROM_LOAD("pl1-5.bin", 0x0c00, 0x0400, 0x4b7ee712);/* foreground lookup table */
            ROM_LOAD("pl1-4.bin", 0x1000, 0x0400, 0x3a7be418);/* background lookup table */
            ROM_END();
        }
    };

    static RomLoadPtr rom_paclandm = new RomLoadPtr() {
        public void handler() {
            ROM_REGION(0x20000, REGION_CPU1, 0);/* 128k for code */
            ROM_LOAD("pl1-1", 0x08000, 0x4000, 0xa938ae99);
            ROM_LOAD("pl1-2", 0x0C000, 0x4000, 0x3fe43bb5);
            /* all the following are banked at 0x4000-0x5fff */
            ROM_LOAD("pl1-3", 0x10000, 0x4000, 0xaa9fa739);
            ROM_LOAD("pl1-4", 0x14000, 0x4000, 0x2b895a90);
            ROM_LOAD("pl1-5", 0x18000, 0x4000, 0x7af66200);
            ROM_LOAD("pl1-6", 0x1c000, 0x4000, 0xb01e59a9);

            ROM_REGION(0x10000, REGION_CPU2, 0);/* 64k for code */
            ROM_LOAD("pl1-7", 0x8000, 0x2000, 0x8c5becae);/* sub program for the mcu */
            ROM_LOAD("pl1-mcu.bin", 0xf000, 0x1000, 0x6ef08fb3);/* microcontroller */

            ROM_REGION(0x02000, REGION_GFX1, ROMREGION_DISPOSE);
            ROM_LOAD("pl1-12", 0x00000, 0x2000, 0xc159fbce);/* chars */

            ROM_REGION(0x02000, REGION_GFX2, ROMREGION_DISPOSE);
            ROM_LOAD("pl1-13", 0x00000, 0x2000, 0x6c5ed9ae);

            ROM_REGION(0x08000, REGION_GFX3, ROMREGION_DISPOSE);
            ROM_LOAD("pl1-9", 0x00000, 0x4000, 0xf5d5962b);/* sprites */
            ROM_LOAD("pl1-10", 0x04000, 0x4000, 0xc7cf1904);

            ROM_REGION(0x08000, REGION_GFX4, ROMREGION_DISPOSE);
            ROM_LOAD("pl1-8", 0x00000, 0x4000, 0xa2ebfa4a);
            ROM_LOAD("pl1-11", 0x04000, 0x4000, 0x6621361a);

            ROM_REGION(0x1400, REGION_PROMS, 0);
            ROM_LOAD("pl1-2.bin", 0x0000, 0x0400, 0x472885de);/* red and green component */
            ROM_LOAD("pl1-1.bin", 0x0400, 0x0400, 0xa78ebdaf);/* blue component */
            ROM_LOAD("pl1-3.bin", 0x0800, 0x0400, 0x80558da8);/* sprites lookup table */
            ROM_LOAD("pl1-5.bin", 0x0c00, 0x0400, 0x4b7ee712);/* foreground lookup table */
            ROM_LOAD("pl1-4.bin", 0x1000, 0x0400, 0x3a7be418);/* background lookup table */
            ROM_END();
        }
    };

    public static GameDriver driver_pacland = new GameDriver("1984", "pacland", "pacland.java", rom_pacland, null, machine_driver_pacland, input_ports_pacland, null, ROT0, "Namco", "Pac-Land (set 1)", GAME_NO_COCKTAIL);
    public static GameDriver driver_pacland2 = new GameDriver("1984", "pacland2", "pacland.java", rom_pacland2, driver_pacland, machine_driver_pacland, input_ports_pacland, null, ROT0, "Namco", "Pac-Land (set 2)", GAME_NO_COCKTAIL);
    public static GameDriver driver_pacland3 = new GameDriver("1984", "pacland3", "pacland.java", rom_pacland3, driver_pacland, machine_driver_pacland, input_ports_pacland, null, ROT0, "Namco", "Pac-Land (set 3)", GAME_NO_COCKTAIL);
    public static GameDriver driver_paclandm = new GameDriver("1984", "paclandm", "pacland.java", rom_paclandm, driver_pacland, machine_driver_pacland, input_ports_pacland, null, ROT0, "[Namco] (Bally Midway license)", "Pac-Land (Midway)", GAME_NO_COCKTAIL);
}
