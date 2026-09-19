
use jni::objects::{JByteArray, JClass, JFloatArray};
use jni::sys::{jboolean, jbyteArray, jfloat, jint, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

use aes_gcm::aead::{Aead, KeyInit, Payload};
use aes_gcm::{Aes256Gcm, Key, Nonce};
use pbkdf2::pbkdf2_hmac;
use sha2::Sha256;

use std::panic::{catch_unwind, AssertUnwindSafe};


fn bytes(env: &JNIEnv, arr: &JByteArray) -> Option<Vec<u8>> {
    env.convert_byte_array(arr).ok()
}

fn to_jbyte_array(env: &JNIEnv, data: &[u8]) -> jbyteArray {
    match env.byte_array_from_slice(data) {
        Ok(a) => a.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}


#[no_mangle]
pub extern "system" fn Java_com_lucent_app_nativebridge_LucentNative_nativePbkdf2Sha256(
    env: JNIEnv,
    _class: JClass,
    password: JByteArray,
    salt: JByteArray,
    iterations: jint,
    key_len: jint,
) -> jbyteArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let pw = bytes(&env, &password)?;
        let st = bytes(&env, &salt)?;
        if iterations <= 0 || key_len <= 0 || key_len > 1024 {
            return None;
        }
        let mut out = vec![0u8; key_len as usize];
        pbkdf2_hmac::<Sha256>(&pw, &st, iterations as u32, &mut out);
        Some(out)
    }));
    match result {
        Ok(Some(out)) => to_jbyte_array(&env, &out),
        _ => std::ptr::null_mut(),
    }
}


fn gcm_cipher(key: &[u8]) -> Option<Aes256Gcm> {
    if key.len() != 32 {
        return None;
    }
    Some(Aes256Gcm::new(Key::<Aes256Gcm>::from_slice(key)))
}

#[no_mangle]
pub extern "system" fn Java_com_lucent_app_nativebridge_LucentNative_nativeAesGcmSeal(
    env: JNIEnv,
    _class: JClass,
    key: JByteArray,
    iv: JByteArray,
    aad: JByteArray,
    plaintext: JByteArray,
) -> jbyteArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let key = bytes(&env, &key)?;
        let iv = bytes(&env, &iv)?;
        let aad = bytes(&env, &aad)?;
        let pt = bytes(&env, &plaintext)?;
        if iv.len() != 12 {
            return None;
        }
        let cipher = gcm_cipher(&key)?;
        cipher
            .encrypt(Nonce::from_slice(&iv), Payload { msg: &pt, aad: &aad })
            .ok()
    }));
    match result {
        Ok(Some(ct)) => to_jbyte_array(&env, &ct),
        _ => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_lucent_app_nativebridge_LucentNative_nativeAesGcmOpen(
    env: JNIEnv,
    _class: JClass,
    key: JByteArray,
    iv: JByteArray,
    aad: JByteArray,
    sealed: JByteArray,
) -> jbyteArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let key = bytes(&env, &key)?;
        let iv = bytes(&env, &iv)?;
        let aad = bytes(&env, &aad)?;
        let ct = bytes(&env, &sealed)?;
        if iv.len() != 12 || ct.len() < 16 {
            return None;
        }
        let cipher = gcm_cipher(&key)?;
        cipher
            .decrypt(Nonce::from_slice(&iv), Payload { msg: &ct, aad: &aad })
            .ok()
    }));
    match result {
        Ok(Some(pt)) => to_jbyte_array(&env, &pt),
        _ => std::ptr::null_mut(),
    }
}


const BLOB_COUNT: usize = 6;
const TAU: f64 = std::f64::consts::PI * 2.0;

const BASE_X: [f64; 6] = [0.30, 0.68, 0.50, 0.22, 0.78, 0.44];
const BASE_Y: [f64; 6] = [0.28, 0.34, 0.62, 0.74, 0.66, 0.20];
const AMP_X: [f64; 6] = [0.20, 0.18, 0.24, 0.16, 0.14, 0.22];
const AMP_Y: [f64; 6] = [0.16, 0.22, 0.14, 0.20, 0.18, 0.24];
const PHASE_X: [f64; 6] = [0.0, 1.1, 2.3, 3.4, 4.6, 5.7];
const PHASE_Y: [f64; 6] = [1.6, 3.0, 0.4, 2.1, 5.0, 3.8];
const SIZE_FACTOR: [f64; 6] = [1.05, 0.85, 1.20, 0.75, 0.95, 1.10];

const PERIOD_X: [f64; 6] = [6900.0, 8200.0, 9500.0, 10800.0, 12100.0, 13400.0];
const PERIOD_Y: [f64; 6] = [8500.0, 9500.0, 10500.0, 11500.0, 12500.0, 13500.0];
const PERIOD_PULSE: [f64; 6] = [3100.0, 3800.0, 4500.0, 5200.0, 5900.0, 6600.0];

const CORNER_CIRCLE: f64 = 1.0;
const CORNER_SQUARE: f64 = 0.44;

const PERIOD_MORPH_A: [f64; 6] = [47000.0, 61000.0, 53000.0, 71000.0, 43000.0, 67000.0];
const PERIOD_MORPH_B: [f64; 6] = [113000.0, 97000.0, 131000.0, 89000.0, 127000.0, 101000.0];
const PHASE_MORPH: [f64; 6] = [0.0, 2.4, 4.1, 1.3, 5.2, 3.3];

const PERIOD_SQUASH: [f64; 6] = [38000.0, 44000.0, 50000.0, 56000.0, 62000.0, 68000.0];
const SQUASH_AMOUNT: f64 = 0.07;

const PERIOD_ROTATE: [f64; 6] = [21000.0, 24000.0, 27000.0, 30000.0, 33000.0, 36000.0];

#[no_mangle]
pub extern "system" fn Java_com_lucent_app_nativebridge_LucentNative_nativeBlobFrame(
    env: JNIEnv,
    _class: JClass,
    t_ms: jfloat,
    width: jfloat,
    height: jfloat,
    out: JFloatArray,
) -> jboolean {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let t = t_ms as f64;
        let w = width as f64;
        let h = height as f64;
        let min_dim = if w < h { w } else { h };
        let base_radius = min_dim * 0.42;

        let mut vals = [0f32; BLOB_COUNT * 6];
        for i in 0..BLOB_COUNT {
            let ax = TAU * (t / PERIOD_X[i]) + PHASE_X[i];
            let ay = TAU * (t / PERIOD_Y[i]) + PHASE_Y[i];

            let pulse_phase = (t / (2.0 * PERIOD_PULSE[i])) % 1.0;
            let triangle = if pulse_phase < 0.5 { pulse_phase * 2.0 } else { (1.0 - pulse_phase) * 2.0 };
            let eased = triangle * triangle * (3.0 - 2.0 * triangle);
            let pulse = 0.82 + 0.36 * eased;

            let cx = (BASE_X[i] + AMP_X[i] * ax.cos()) * w;
            let cy = (BASE_Y[i] + AMP_Y[i] * ay.sin()) * h;
            let radius = base_radius * SIZE_FACTOR[i] * pulse;

            let morph_raw = 0.62 * (TAU * (t / PERIOD_MORPH_A[i]) + PHASE_MORPH[i]).sin()
                + 0.38 * (TAU * (t / PERIOD_MORPH_B[i])).sin();
            let morph01 = ((morph_raw + 1.0) * 0.5).clamp(0.0, 1.0);
            let morph = morph01 * morph01 * (3.0 - 2.0 * morph01);
            let corner = CORNER_CIRCLE + (CORNER_SQUARE - CORNER_CIRCLE) * morph;

            let squash = 1.0 + SQUASH_AMOUNT * (TAU * (t / PERIOD_SQUASH[i]) + PHASE_MORPH[i]).sin();

            let dir = if i % 2 == 0 { 1.0 } else { -1.0 };
            let angle_deg = dir * (t / PERIOD_ROTATE[i]) * 360.0;

            let o = i * 6;
            vals[o] = cx as f32;
            vals[o + 1] = cy as f32;
            vals[o + 2] = radius as f32;
            vals[o + 3] = corner as f32;
            vals[o + 4] = squash as f32;
            vals[o + 5] = angle_deg as f32;
        }
        env.set_float_array_region(&out, 0, &vals).is_ok()
    }));
    match result {
        Ok(true) => JNI_TRUE,
        _ => JNI_FALSE,
    }
}


#[cfg(test)]
mod tests {
    use super::*;

    fn hex(data: &[u8]) -> String {
        data.iter().map(|b| format!("{:02x}", b)).collect()
    }

    #[test]
    fn pbkdf2_matches_reference() {
        let salt: Vec<u8> = (0u8..16).collect();
        let mut out = [0u8; 32];
        pbkdf2_hmac::<Sha256>(b"Lucent-backup-passphrase-v1", &salt, 10_000, &mut out);
        assert_eq!(
            hex(&out),
            "77297f53ee820b664c9fe17f392f60aa8fedbd42b5d309d5542b0ca07469aaf7"
        );
    }

    #[test]
    fn aes_gcm_matches_reference_and_round_trips() {
        let key: Vec<u8> = (0u8..32).collect();
        let iv: Vec<u8> = (0u8..12).collect();
        let aad = [1u8, 0, 0, 0, 7];
        let cipher = gcm_cipher(&key).unwrap();
        let ct = cipher
            .encrypt(Nonce::from_slice(&iv), Payload { msg: b"hello lucent", aad: &aad })
            .unwrap();
        assert_eq!(hex(&ct), "2f67ba77aac5ae6eee24f9ff577f858f60177ef51ef87b847946a4fe");
        let pt = cipher
            .decrypt(Nonce::from_slice(&iv), Payload { msg: &ct, aad: &aad })
            .unwrap();
        assert_eq!(pt, b"hello lucent");
        let mut bad = ct.clone();
        bad[0] ^= 1;
        assert!(cipher
            .decrypt(Nonce::from_slice(&iv), Payload { msg: &bad, aad: &aad })
            .is_err());
    }

    #[test]
    fn blob_math_matches_kotlin_formulas() {
        let t = 1234.5f64;
        let (w, h) = (1000.0f64, 2000.0f64);
        let i = 0usize;

        let ax = TAU * (t / PERIOD_X[i]) + PHASE_X[i];
        let pulse_phase = (t / (2.0 * PERIOD_PULSE[i])) % 1.0;
        let triangle = if pulse_phase < 0.5 { pulse_phase * 2.0 } else { (1.0 - pulse_phase) * 2.0 };
        let eased = triangle * triangle * (3.0 - 2.0 * triangle);
        let pulse = 0.82 + 0.36 * eased;
        let expected_cx = ((BASE_X[i] + AMP_X[i] * ax.cos()) * w) as f32;
        let expected_radius = ((w.min(h)) * 0.42 * SIZE_FACTOR[i] * pulse) as f32;

        let min_dim = w.min(h);
        let base_radius = min_dim * 0.42;
        let cx = ((BASE_X[i] + AMP_X[i] * ax.cos()) * w) as f32;
        let radius = (base_radius * SIZE_FACTOR[i] * pulse) as f32;

        assert_eq!(cx, expected_cx);
        assert_eq!(radius, expected_radius);
        assert!((0.44..=1.0).contains(&(CORNER_SQUARE)));
    }
}


#[no_mangle]
pub extern "system" fn Java_com_lucent_app_nativebridge_LucentNative_nativeCpuHasAvx2(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    #[cfg(target_arch = "x86_64")]
    {
        if std::arch::is_x86_feature_detected!("avx2") { JNI_TRUE } else { JNI_FALSE }
    }
    #[cfg(not(target_arch = "x86_64"))]
    {
        JNI_TRUE
    }
}
