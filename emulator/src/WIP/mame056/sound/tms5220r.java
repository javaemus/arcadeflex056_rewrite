/**
 * ported to v0.56
 * ported to v0.37b7
 *
 */
package WIP.mame056.sound;

public class tms5220r {

    /* TMS5220 ROM Tables */

 /* This is the energy lookup table (4-bits -> 10-bits) */
    public static int energytable[] = {
        0x0000, 0x00C0, 0x0140, 0x01C0, 0x0280, 0x0380, 0x0500, 0x0740,
        0x0A00, 0x0E40, 0x1440, 0x1C80, 0x2840, 0x38C0, 0x5040, 0x7FC0};

    /* This is the pitch lookup table (6-bits -> 8-bits) */
    public static int pitchtable[] = {
        0x0000, 0x1000, 0x1100, 0x1200, 0x1300, 0x1400, 0x1500, 0x1600,
        0x1700, 0x1800, 0x1900, 0x1A00, 0x1B00, 0x1C00, 0x1D00, 0x1E00,
        0x1F00, 0x2000, 0x2100, 0x2200, 0x2300, 0x2400, 0x2500, 0x2600,
        0x2700, 0x2800, 0x2900, 0x2A00, 0x2B00, 0x2D00, 0x2F00, 0x3100,
        0x3300, 0x3500, 0x3600, 0x3900, 0x3B00, 0x3D00, 0x3F00, 0x4200,
        0x4500, 0x4700, 0x4900, 0x4D00, 0x4F00, 0x5100, 0x5500, 0x5700,
        0x5C00, 0x5F00, 0x6300, 0x6600, 0x6A00, 0x6E00, 0x7300, 0x7700,
        0x7B00, 0x8000, 0x8500, 0x8A00, 0x8F00, 0x9500, 0x9A00, 0xA000};

    /* These are the reflection coefficient lookup tables */

 /* K1 is (5-bits -> 9 bits+sign, 2's comp. fractional (-1 < x < 1) */
    public static short k1table[] = {
        (short) 0x82C0, (short) 0x8380, (short) 0x83C0, (short) 0x8440, (short) 0x84C0, (short) 0x8540, (short) 0x8600, (short) 0x8780,
        (short) 0x8880, (short) 0x8980, (short) 0x8AC0, (short) 0x8C00, (short) 0x8D40, (short) 0x8F00, (short) 0x90C0, (short) 0x92C0,
        (short) 0x9900, (short) 0xA140, (short) 0xAB80, (short) 0xB840, (short) 0xC740, (short) 0xD8C0, (short) 0xEBC0, 0x0000,
        0x1440, 0x2740, 0x38C0, 0x47C0, 0x5480, 0x5EC0, 0x6700, 0x6D40};

    /* K2 is (5-bits -> 9 bits+sign, 2's comp. fractional (-1 < x < 1) */
    public static short k2table[] = {
        (short) 0xAE00, (short) 0xB480, (short) 0xBB80, (short) 0xC340, (short) 0xCB80, (short) 0xD440, (short) 0xDDC0, (short) 0xE780,
        (short) 0xF180, (short) 0xFBC0, 0x0600, 0x1040, 0x1A40, 0x2400, 0x2D40, 0x3600,
        0x3E40, 0x45C0, 0x4CC0, 0x5300, 0x5880, 0x5DC0, 0x6240, 0x6640,
        0x69C0, 0x6CC0, 0x6F80, 0x71C0, 0x73C0, 0x7580, 0x7700, 0x7E80};

    /* K3 is (4-bits -> 9 bits+sign, 2's comp. fractional (-1 < x < 1) */
    public static short k3table[] = {
        (short) 0x9200, (short) 0x9F00, (short) 0xAD00, (short) 0xBA00, (short) 0xC800, (short) 0xD500, (short) 0xE300, (short) 0xF000,
        (short) 0xFE00, 0x0B00, 0x1900, 0x2600, 0x3400, 0x4100, 0x4F00, 0x5C00};

    /* K4 is (4-bits -> 9 bits+sign, 2's comp. fractional (-1 < x < 1) */
    public static short k4table[] = {
        (short) 0xAE00, (short) 0xBC00, (short) 0xCA00, (short) 0xD800, (short) 0xE600, (short) 0xF400, 0x0100, 0x0F00,
        0x1D00, 0x2B00, 0x3900, 0x4700, 0x5500, 0x6300, 0x7100, 0x7E00};

    /* K5 is (4-bits -> 9 bits+sign, 2's comp. fractional (-1 < x < 1) */
    public static short k5table[] = {
        (short) 0xAE00, (short) 0xBA00, (short) 0xC500, (short) 0xD100, (short) 0xDD00, (short) 0xE800, (short) 0xF400, (short) 0xFF00,
        0x0B00, 0x1700, 0x2200, 0x2E00, 0x3900, 0x4500, 0x5100, 0x5C00};

    /* K6 is (4-bits -> 9 bits+sign, 2's comp. fractional (-1 < x < 1) */
    public static short k6table[] = {
        (short) 0xC000, (short) 0xCB00, (short) 0xD600, (short) 0xE100, (short) 0xEC00, (short) 0xF700, 0x0300, 0x0E00,
        0x1900, 0x2400, 0x2F00, 0x3A00, 0x4500, 0x5000, 0x5B00, 0x6600};

    /* K7 is (4-bits -> 9 bits+sign, 2's comp. fractional (-1 < x < 1) */
    public static short k7table[] = {
        (short) 0xB300, (short) 0xBF00, (short) 0xCB00, (short) 0xD700, (short) 0xE300, (short) 0xEF00, (short) 0xFB00, 0x0700,
        0x1300, 0x1F00, 0x2B00, 0x3700, 0x4300, 0x4F00, 0x5A00, 0x6600};

    /* K8 is (3-bits -> 9 bits+sign, 2's comp. fractional (-1 < x < 1) */
    public static short k8table[] = {
        (short) 0xC000, (short) 0xD800, (short) 0xF000, 0x0700, 0x1F00, 0x3700, 0x4F00, 0x6600};

    /* K9 is (3-bits -> 9 bits+sign, 2's comp. fractional (-1 < x < 1) */
    public static short k9table[] = {
        (short) 0xC000, (short) 0xD400, (short) 0xE800, (short) 0xFC00, 0x1000, 0x2500, 0x3900, 0x4D00};

    /* K10 is (3-bits -> 9 bits+sign, 2's comp. fractional (-1 < x < 1) */
    public static short k10table[] = {
        (short) 0xCD00, (short) 0xDF00, (short) 0xF100, 0x0400, 0x1600, 0x2000, 0x3B00, 0x4D00};

    /* chirp table */
    public static char chirptable[] = {
        0x00, 0x2a, (char) 0xd4, 0x32,
        (char) 0xb2, 0x12, 0x25, 0x14,
        0x02, (char) 0xe1, (char) 0xc5, 0x02,
        0x5f, 0x5a, 0x05, 0x0f,
        0x26, (char) 0xfc, (char) 0xa5, (char) 0xa5,
        (char) 0xd6, (char) 0xdd, (char) 0xdc, (char) 0xfc,
        0x25, 0x2b, 0x22, 0x21,
        0x0f, (char) 0xff, (char) 0xf8, (char) 0xee,
        (char) 0xed, (char) 0xef, (char) 0xf7, (char) 0xf6,
        (char) 0xfa, 0x00, 0x03, 0x02,
        0x01
    };

    /* interpolation coefficients */
    public static char interp_coeff[] = {
        8, 8, 8, 4, 4, 2, 2, 1
    };
}
