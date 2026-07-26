use jni::objects::{JClass, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;

#[no_mangle]
pub extern "system" fn Java_com_example_viengines_ViEngine_transform(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
    method: jint, // 0: TELEX, 1: VNI
) -> jstring {
    let input_str: String = match env.get_string(&input) {
        Ok(js) => js.into(),
        Err(_) => return env.new_string("").expect("Couldn't create java string").into_raw(),
    };

    let mut result = String::new();
    if method == 0 {
        vi::telex::transform_buffer(input_str.chars(), &mut result);
    } else {
        vi::vni::transform_buffer(input_str.chars(), &mut result);
    }

    let output = env.new_string(&result).expect("Couldn't create java string");
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_aistudio_vietnamesekeyboard_viengines_ViEngine_transform(
    env: JNIEnv,
    class: JClass,
    input: JString,
    method: jint,
) -> jstring {
    Java_com_example_viengines_ViEngine_transform(env, class, input, method)
}
