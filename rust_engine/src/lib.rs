use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jint, jstring};
use vi::methods::{transform_buffer, TELEX, VNI};

/// JNI native interface for ViEngine.
/// 
/// Method signature matching `Java_com_example_viengines_ViEngine_transform`:
/// - `method == 0`: Telex transformation using `vi::methods::TELEX`
/// - `method == 1`: VNI transformation using `vi::methods::VNI`
#[no_mangle]
pub extern "system" fn Java_com_example_viengines_ViEngine_transform(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
    method: jint,
) -> jstring {
    // 1. Safely extract Rust String from JString with error handling
    let input_str: String = match env.get_string(&input) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    // 2. Prepare output buffer and select method definition
    let mut result = String::with_capacity(input_str.len());
    let engine_method = if method == 0 { &TELEX } else { &VNI };

    // 3. Perform buffer transformation using vi crate (0.8.0) API
    transform_buffer(engine_method, input_str.chars(), &mut result);

    // 4. Return new JString safely
    let output = match env.new_string(&result) {
        Ok(s) => s,
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };
    output.into_raw()
}
