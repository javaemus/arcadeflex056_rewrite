/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */
package WIP.mame056.drivers;

import static WIP2.arcadeflex056.fucPtr.*;

import static WIP2.mame056.commonH.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.inptportH.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.driverH.*;
import static WIP2.mame056.memoryH.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.sndintrfH.*;

import static WIP2.mame056.sound.ay8910.*;
import static WIP2.mame056.sound.ay8910H.*;

import static WIP.mame056.vidhrdw.mole.*;

public class mole {

    static GfxLayout tile_layout = new GfxLayout(
            8, 8, /* character size */
            512, /* number of characters */
            3, /* number of bitplanes */
            new int[]{0x0000 * 8, 0x1000 * 8, 0x2000 * 8},
            new int[]{0, 1, 2, 3, 4, 5, 6, 7},
            new int[]{0 * 8, 1 * 8, 2 * 8, 3 * 8, 4 * 8, 5 * 8, 6 * 8, 7 * 8},
            8 * 8
    );

    static GfxDecodeInfo gfx_decode[] = {
        new GfxDecodeInfo(1, 0x0000, tile_layout, 0x00, 1),
        new GfxDecodeInfo(1, 0x3000, tile_layout, 0x00, 1),
        new GfxDecodeInfo(-1)
    };

    static AY8910interface ay8910_interface = new AY8910interface(
            1, /* 1 chip */
            2000000, /* 2 MHz? */
            new int[]{100}, /* volume */
            new ReadHandlerPtr[]{null},
            new ReadHandlerPtr[]{null},
            new WriteHandlerPtr[]{null},
            new WriteHandlerPtr[]{null}
    );

    public static ReadHandlerPtr mole_prot_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            /*	Following are all known examples of Mole Attack
		**	code reading from the protection circuitry:
		**
		**	5b0b:
		**	ram[0x0361] = (ram[0x885+ram[0x8a5])&ram[0x886]
		**	ram[0x0363] = ram[0x886]
		**
		**	53c9:
		**	ram[0xe0] = ram[0x800]+ram[0x802]+ram[0x804]
		**	ram[0xea] = ram[0x828]
		**
		**	ram[0xe2] = (ram[0x806]&ram[0x826])|ram[0x820]
		**	ram[0xe3] = ram[0x826]
		**
		**	ram[0x361] = (ram[0x8cd]&ram[0x8ad])|ram[0x8ce]
		**	ram[0x362] = ram[0x8ae] = 0x32
		**
		**	ram[0x363] = ram[0x809]+ram[0x829]+ram[0x828]
		**	ram[0x364] = ram[0x808]
             */

            switch (offset) {
                case 0x08:
                    return 0xb0;
                /* random mole placement */
                case 0x26:
                    if (cpu_get_pc() == 0x53d7) {
                        return 0x06;
                        /* bonus round */
                    } else { // pc == 0x515b, 0x5162
                        return 0xc6;
                        /* game start */
                    }
                case 0x86:
                    return 0x91;
                /* game over */
                case 0xae:
                    return 0x32;
                /* coinage */
            }

            /*	The above are critical protection reads.
		**	It isn't clear what effect (if any) the
		**	remaining reads have; for now we simply
		**	return 0x00
             */
            return 0x00;
        }
    };

    public static Memory_ReadAddress moleattack_readmem[] = {
        new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
        new Memory_ReadAddress(0x0000, 0x03ff, MRA_RAM),
        new Memory_ReadAddress(0x0800, 0x08ff, mole_prot_r),
        new Memory_ReadAddress(0x5000, 0x7fff, MRA_ROM),
        new Memory_ReadAddress(0x8d00, 0x8d00, input_port_0_r),
        new Memory_ReadAddress(0x8d40, 0x8d40, input_port_1_r),
        new Memory_ReadAddress(0x8d80, 0x8d80, input_port_2_r),
        new Memory_ReadAddress(0x8dc0, 0x8dc0, input_port_3_r),
        new Memory_ReadAddress(0xd000, 0xffff, MRA_ROM),
        new Memory_ReadAddress(MEMPORT_MARKER, 0)
    };

    public static Memory_WriteAddress moleattack_writemem[] = {
        new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
        new Memory_WriteAddress(0x0000, 0x03ff, MWA_RAM),
        new Memory_WriteAddress(0x5000, 0x7fff, MWA_ROM),
        new Memory_WriteAddress(0x8000, 0x83ff, moleattack_videoram_w),
        new Memory_WriteAddress(0x8400, 0x8400, moleattack_tilesetselector_w),
        new Memory_WriteAddress(0x8c00, 0x8c00, AY8910_write_port_0_w),
        new Memory_WriteAddress(0x8c01, 0x8c01, AY8910_control_port_0_w),
        new Memory_WriteAddress(0x8d00, 0x8d00, MWA_NOP), /* watchdog? */
        new Memory_WriteAddress(0xd000, 0xffff, MWA_ROM),
        new Memory_WriteAddress(MEMPORT_MARKER, 0)
    };

    static MachineDriver machine_driver_mole = new MachineDriver(
            new MachineCPU[]{
                new MachineCPU(CPU_M6502,
                        4000000, /* ? */
                        moleattack_readmem, moleattack_writemem, null, null,
                        interrupt, 1
                )
            },
            60, DEFAULT_60HZ_VBLANK_DURATION,
            1, /* single CPU */
            null,
            /* video hardware */
            40 * 8, 25 * 8, new rectangle(0 * 8, 40 * 8 - 1, 0 * 8, 25 * 8 - 1),
            gfx_decode,
            8, 8, moleattack_vh_convert_color_prom,
            VIDEO_TYPE_RASTER,
            null,
            moleattack_vh_start,
            moleattack_vh_stop,
            moleattack_vh_screenrefresh,
            0, 0, 0, 0,
            new MachineSound[]{
                new MachineSound(
                        SOUND_AY8910,
                        ay8910_interface
                )
            }
    );

    static RomLoadPtr rom_mole = new RomLoadPtr() {
        public void handler() {
            /* ALL ROMS ARE 2732 */
            ROM_REGION(0x10000, REGION_CPU1, 0);/* 64k for 6502 code */
            ROM_LOAD("m3a.5h", 0x5000, 0x1000, 0x5fbbdfef);
            ROM_RELOAD(0xd000, 0x1000);
            ROM_LOAD("m2a.7h", 0x6000, 0x1000, 0xf2a90642);
            ROM_RELOAD(0xe000, 0x1000);
            ROM_LOAD("m1a.8h", 0x7000, 0x1000, 0xcff0119a);
            ROM_RELOAD(0xf000, 0x1000);

            ROM_REGION(0x6000, REGION_GFX1, ROMREGION_DISPOSE);
            ROM_LOAD("mea.4a", 0x0000, 0x1000, 0x49d89116);
            ROM_LOAD("mca.6a", 0x1000, 0x1000, 0x04e90300);
            ROM_LOAD("maa.9a", 0x2000, 0x1000, 0x6ce9442b);
            ROM_LOAD("mfa.3a", 0x3000, 0x1000, 0x0d0c7d13);
            ROM_LOAD("mda.5a", 0x4000, 0x1000, 0x41ae1842);
            ROM_LOAD("mba.8a", 0x5000, 0x1000, 0x50c43fc9);
            ROM_END();
        }
    };

    static InputPortPtr input_ports_mole = new InputPortPtr() {
        public void handler() {
            PORT_START();  // 0x8d00
            PORT_DIPNAME(0x01, 0x00, "Round Points");
            PORT_DIPSETTING(0x00, DEF_STR("Off"));
            PORT_DIPSETTING(0x01, DEF_STR("On"));
            PORT_DIPNAME(0x02, 0x00, DEF_STR("Coinage"));
            PORT_DIPSETTING(0x00, DEF_STR("1C_1C"));
            PORT_DIPSETTING(0x02, DEF_STR("1C_2C"));
            PORT_DIPNAME(0x0c, 0x00, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x00, "A");
            PORT_DIPSETTING(0x04, "B");
            PORT_DIPSETTING(0x08, "C");
            PORT_DIPSETTING(0x0c, "D");
            PORT_DIPNAME(0x30, 0x00, DEF_STR("Unknown"));
            PORT_DIPSETTING(0x00, "A");
            PORT_DIPSETTING(0x10, "B");
            PORT_DIPSETTING(0x20, "C");
            PORT_DIPSETTING(0x30, "D");
            PORT_DIPNAME(0x40, 0x40, DEF_STR("Unused"));
            /* unused */
            PORT_DIPSETTING(0x00, DEF_STR("Off"));
            PORT_DIPSETTING(0x40, DEF_STR("On"));
            PORT_DIPNAME(0x80, 0x80, DEF_STR("Unused"));
            /* unused */
            PORT_DIPSETTING(0x00, DEF_STR("Off"));
            PORT_DIPSETTING(0x80, DEF_STR("On"));

            PORT_START();  // 0x8d40
            PORT_BIT(0x01, IP_ACTIVE_HIGH, IPT_BUTTON1);
            PORT_BIT(0x02, IP_ACTIVE_HIGH, IPT_BUTTON2);
            PORT_BIT(0x04, IP_ACTIVE_HIGH, IPT_BUTTON3);
            PORT_BIT(0x08, IP_ACTIVE_HIGH, IPT_BUTTON4);
            PORT_BIT(0x10, IP_ACTIVE_HIGH, IPT_BUTTON5);
            PORT_BIT(0x20, IP_ACTIVE_HIGH, IPT_BUTTON6);
            PORT_BIT(0x40, IP_ACTIVE_HIGH, IPT_BUTTON7);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_BUTTON8);

            PORT_START();  // 0x8d80
            PORT_BIT(0x01, IP_ACTIVE_HIGH, IPT_BUTTON9);
            PORT_BIT(0x02, IP_ACTIVE_HIGH, IPT_BUTTON1 | IPF_COCKTAIL);
            PORT_BIT(0x04, IP_ACTIVE_HIGH, IPT_BUTTON2 | IPF_COCKTAIL);
            PORT_BIT(0x08, IP_ACTIVE_HIGH, IPT_BUTTON3 | IPF_COCKTAIL);
            PORT_DIPNAME(0x10, 0x00, DEF_STR("Cabinet"));
            PORT_DIPSETTING(0x00, DEF_STR("Upright"));
            PORT_DIPSETTING(0x10, DEF_STR("Cocktail"));
            PORT_BIT(0x20, IP_ACTIVE_HIGH, IPT_START2);
            PORT_BIT(0x40, IP_ACTIVE_HIGH, IPT_START1);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_COIN1);

            PORT_START();  // 0x8dc0
            PORT_BIT(0x01, IP_ACTIVE_HIGH, IPT_BUTTON8 | IPF_COCKTAIL);
            PORT_BIT(0x02, IP_ACTIVE_HIGH, IPT_BUTTON7 | IPF_COCKTAIL);
            PORT_BIT(0x04, IP_ACTIVE_HIGH, IPT_BUTTON4 | IPF_COCKTAIL);
            PORT_BIT(0x08, IP_ACTIVE_HIGH, IPT_BUTTON9 | IPF_COCKTAIL);
            PORT_BIT(0x10, IP_ACTIVE_HIGH, IPT_BUTTON6 | IPF_COCKTAIL);
            PORT_BIT(0x20, IP_ACTIVE_HIGH, IPT_BUTTON5 | IPF_COCKTAIL);
            PORT_BIT(0x40, IP_ACTIVE_HIGH, IPT_UNUSED);
            PORT_BIT(0x80, IP_ACTIVE_HIGH, IPT_UNUSED);
            INPUT_PORTS_END();
        }
    };

    public static GameDriver driver_mole = new GameDriver("1982", "mole", "mole.java", rom_mole, null, machine_driver_mole, input_ports_mole, null, ROT0, "Yachiyo Electronics, Ltd.", "Mole Attack");
}
