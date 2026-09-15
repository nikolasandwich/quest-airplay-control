// SPDX-License-Identifier: GPL-3.0-or-later
// Exercise the actual RPiPlay wrappers against the Android crypto dependency.
#include "crypto.h"
#include <openssl/crypto.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// A static Bionic executable needs an ARM64-aligned TLS segment under QEMU.
static __thread unsigned char tls_anchor __attribute__((aligned(64)));

static void check(int ok, const char *name) {
    if (!ok) { fprintf(stderr, "FAIL: %s\n", name); exit(1); }
    printf("PASS: %s\n", name);
}

int main(void) {
    tls_anchor = 1;
    check(OPENSSL_version_major() == 3, "OpenSSL 3 runtime");
    printf("%s\n", OpenSSL_version(OPENSSL_VERSION));
    // NIST SP 800-38A, F.1/F.2/F.5 AES-128 first block.
    const unsigned char key[16] = {0x2b,0x7e,0x15,0x16,0x28,0xae,0xd2,0xa6,0xab,0xf7,0x15,0x88,0x09,0xcf,0x4f,0x3c};
    const unsigned char iv[16] = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15};
    const unsigned char ctr[16] = {0xf0,0xf1,0xf2,0xf3,0xf4,0xf5,0xf6,0xf7,0xf8,0xf9,0xfa,0xfb,0xfc,0xfd,0xfe,0xff};
    const unsigned char plain[16] = {0x6b,0xc1,0xbe,0xe2,0x2e,0x40,0x9f,0x96,0xe9,0x3d,0x7e,0x11,0x73,0x93,0x17,0x2a};
    const unsigned char cbc_expected[16] = {0x76,0x49,0xab,0xac,0x81,0x19,0xb2,0x46,0xce,0xe9,0x8e,0x9b,0x12,0xe9,0x19,0x7d};
    const unsigned char ctr_expected[16] = {0x87,0x4d,0x61,0x91,0xb6,0x20,0xe3,0x26,0x1b,0xef,0x68,0x64,0x99,0x0d,0xb6,0xce};
    unsigned char out[64];
    aes_ctx_t *aes = aes_ctr_init(key, ctr);
    aes_ctr_encrypt(aes, plain, out, 7);
    aes_ctr_encrypt(aes, plain + 7, out + 7, 9);
    check(!memcmp(out, ctr_expected, 16), "AES CTR fragmented known vector");
    aes_ctr_reset(aes);
    aes_ctr_decrypt(aes, ctr_expected, out, 16);
    check(!memcmp(out, plain, 16), "AES CTR reset/decrypt");
    aes_ctr_destroy(aes);
    aes = aes_cbc_init(key, iv, AES_ENCRYPT);
    aes_cbc_encrypt(aes, plain, out, 16);
    check(!memcmp(out, cbc_expected, 16), "AES CBC known vector");
    aes_cbc_destroy(aes);
    aes = aes_cbc_init(key, iv, AES_DECRYPT);
    aes_cbc_decrypt(aes, cbc_expected, out, 16);
    check(!memcmp(out, plain, 16), "AES CBC decrypt");
    aes_cbc_destroy(aes);

    const unsigned char digest[64] = {
        0xdd,0xaf,0x35,0xa1,0x93,0x61,0x7a,0xba,0xcc,0x41,0x73,0x49,0xae,0x20,0x41,0x31,
        0x12,0xe6,0xfa,0x4e,0x89,0xa9,0x7e,0xa2,0x0a,0x9e,0xee,0xe6,0x4b,0x55,0xd3,0x9a,
        0x21,0x92,0x99,0x2a,0x27,0x4f,0xc1,0xa8,0x36,0xba,0x3c,0x23,0xa3,0xfe,0xeb,0xbd,
        0x45,0x4d,0x44,0x23,0x64,0x3c,0xe8,0x0e,0x2a,0x9a,0xc9,0x4f,0xa5,0x4c,0xa4,0x9f};
    sha_ctx_t *sha = sha_init();
    unsigned int len = 0;
    sha_update(sha, (const unsigned char *)"abc", 3);
    sha_final(sha, out, &len);
    check(len == 64 && !memcmp(out, digest, 64), "SHA512 known vector");
    sha_destroy(sha);

    x25519_key_t *a = x25519_key_generate(), *b = x25519_key_generate();
    unsigned char raw[32], left[32], right[32];
    x25519_key_get_raw(raw, b);
    x25519_key_t *bp = x25519_key_from_raw(raw);
    x25519_key_get_raw(raw, a);
    x25519_key_t *ap = x25519_key_from_raw(raw);
    x25519_derive_secret(left, a, bp);
    x25519_derive_secret(right, b, ap);
    const unsigned char zero[32] = {0};
    check(!memcmp(left, right, 32) && memcmp(left, zero, 32), "X25519 peer agreement");
    x25519_key_destroy(a); x25519_key_destroy(b);
    x25519_key_destroy(ap); x25519_key_destroy(bp);

    ed25519_key_t *signer = ed25519_key_generate();
    ed25519_key_get_raw(raw, signer);
    ed25519_key_t *verifier = ed25519_key_from_raw(raw);
    ed25519_sign(out, 64, plain, 16, signer);
    check(ed25519_verify(out, 64, plain, 16, verifier) == 1, "Ed25519 peer signature");
    out[0] ^= 1;
    check(ed25519_verify(out, 64, plain, 16, verifier) != 1, "Ed25519 reject tampered signature");
    ed25519_key_destroy(signer); ed25519_key_destroy(verifier);
    return 0;
}
