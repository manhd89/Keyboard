use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jint, jstring};

#[no_mangle]
pub extern "system" fn Java_com_example_viengines_ViEngine_transform(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
    method: jint,
) -> jstring {
    let input_str: String = match env.get_string(&input) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    let result = if method == 0 {
        process_telex(&input_str)
    } else {
        process_vni(&input_str)
    };

    let output = env.new_string(&result).expect("Couldn't create Java string");
    output.into_raw()
}

fn process_telex(input: &str) -> String {
    let mut s = input.to_string();

    // Standalone 'w' / 'W'
    if s.eq_ignore_ascii_case("w") {
        return if s.starts_with('W') { "Ư".to_string() } else { "ư".to_string() };
    }

    // dd / DD -> đ / Đ
    s = s.replace("dd", "đ")
         .replace("Dd", "Đ")
         .replace("DD", "Đ")
         .replace("dD", "Đ");

    // Double vowel rules
    s = replace_pair(&s, "aa", 'â', 'a');
    s = replace_pair(&s, "AA", 'Â', 'A');
    s = replace_pair(&s, "Aa", 'Â', 'A');

    s = replace_pair(&s, "aw", 'ă', 'a');
    s = replace_pair(&s, "AW", 'Ă', 'A');
    s = replace_pair(&s, "Aw", 'Ă', 'A');

    s = replace_pair(&s, "ee", 'ê', 'e');
    s = replace_pair(&s, "EE", 'Ê', 'E');
    s = replace_pair(&s, "Ee", 'Ê', 'E');

    s = replace_pair(&s, "oo", 'ô', 'o');
    s = replace_pair(&s, "OO", 'Ô', 'O');
    s = replace_pair(&s, "Oo", 'Ô', 'O');

    s = replace_pair(&s, "ow", 'ơ', 'o');
    s = replace_pair(&s, "OW", 'Ơ', 'O');
    s = replace_pair(&s, "Ow", 'Ơ', 'O');

    s = replace_pair(&s, "uw", 'ư', 'u');
    s = replace_pair(&s, "UW", 'Ư', 'U');
    s = replace_pair(&s, "Uw", 'Ư', 'U');

    if s.contains('w') || s.contains('W') {
        s = s.replace("uw", "ư").replace("UW", "Ư").replace("Uw", "Ư");
        s = s.replace("ow", "ơ").replace("OW", "Ơ").replace("Ow", "Ơ");
        s = s.replace("w", "ư").replace("W", "Ư");
    }

    // Tones: s (1), f (2), r (3), x (4), j (5), z (0)
    s = apply_tone_mark(&s, 's', 1);
    s = apply_tone_mark(&s, 'f', 2);
    s = apply_tone_mark(&s, 'r', 3);
    s = apply_tone_mark(&s, 'x', 4);
    s = apply_tone_mark(&s, 'j', 5);
    s = apply_tone_mark(&s, 'z', 0);

    s
}

fn process_vni(input: &str) -> String {
    let mut s = input.to_string();

    s = s.replace("d9", "đ").replace("D9", "Đ");

    s = apply_vni_hat(&s, '6', &[('a', 'â'), ('e', 'ê'), ('o', 'ô'), ('A', 'Â'), ('E', 'Ê'), ('O', 'Ô')]);
    s = apply_vni_hat(&s, '7', &[('o', 'ơ'), ('u', 'ư'), ('O', 'Ơ'), ('U', 'Ư')]);
    s = apply_vni_hat(&s, '8', &[('a', 'ă'), ('A', 'Ă')]);

    s = apply_tone_mark(&s, '1', 1);
    s = apply_tone_mark(&s, '2', 2);
    s = apply_tone_mark(&s, '3', 3);
    s = apply_tone_mark(&s, '4', 4);
    s = apply_tone_mark(&s, '5', 5);
    s = apply_tone_mark(&s, '0', 0);

    s
}

fn replace_pair(str: &str, target: &str, replacement: char, _base: char) -> String {
    if let Some(idx) = str.to_lowercase().find(&target.to_lowercase()) {
        let mut chars: Vec<char> = str.chars().collect();
        if idx < chars.len() {
            chars[idx] = replacement;
            if idx + 1 < chars.len() {
                chars.remove(idx + 1);
            }
            return chars.into_iter().collect();
        }
    }
    str.to_string()
}

fn apply_vni_hat(str: &str, key: char, map: &[(char, char)]) -> String {
    if !str.contains(key) {
        return str.to_string();
    }
    let mut chars: Vec<char> = str.chars().collect();
    if let Some(key_idx) = chars.iter().rposition(|&c| c == key) {
        chars.remove(key_idx);
        for i in (0..key_idx).rev() {
            let c = chars[i];
            for &(from, to) in map {
                if c == from {
                    chars[i] = to;
                    return chars.into_iter().collect();
                }
            }
        }
    }
    str.to_string()
}

fn apply_tone_mark(str: &str, key: char, tone: u8) -> String {
    if !str.contains(key) {
        return str.to_string();
    }
    let mut chars: Vec<char> = str.chars().collect();
    if let Some(key_idx) = chars.iter().rposition(|&c| c.to_ascii_lowercase() == key) {
        chars.remove(key_idx);
        
        let target_vowel_idx = find_vowel_to_mark(&chars);
        if let Some(v_idx) = target_vowel_idx {
            chars[v_idx] = set_char_tone(chars[v_idx], tone);
        }
        return chars.into_iter().collect();
    }
    str.to_string()
}

fn find_vowel_to_mark(chars: &[char]) -> Option<usize> {
    let vowels = "aăâeêioôơuưyAĂÂEÊIOÔƠUƯY";
    let indices: Vec<usize> = chars.iter().enumerate()
        .filter(|(_, &c)| vowels.contains(c))
        .map(|(i, _)| i)
        .collect();

    if indices.is_empty() {
        return None;
    }
    if indices.len() == 1 {
        return Some(indices[0]);
    }

    for &idx in &indices {
        let c = chars[idx];
        if "êơưôâăÊƠƯÔÂĂ".contains(c) {
            return Some(idx);
        }
    }

    Some(indices[1])
}

fn set_char_tone(c: char, tone: u8) -> char {
    let base = match c {
        'á' | 'à' | 'ả' | 'ã' | 'ạ' | 'a' => 'a',
        'Á' | 'À' | 'Ả' | 'Ã' | 'Ạ' | 'A' => 'A',
        'ắ' | 'ằ' | 'ẳ' | 'ẵ' | 'ặ' | 'ă' => 'ă',
        'Ắ' | 'Ằ' | 'Ẳ' | 'Ẵ' | 'Ặ' | 'Ă' => 'Ă',
        'ấ' | 'ầ' | 'ẩ' | 'ẫ' | 'ậ' | 'â' => 'â',
        'Ấ' | 'Ầ' | 'Ẩ' | 'Ẫ' | 'Ậ' | 'Â' => 'Â',
        'é' | 'è' | 'ẻ' | 'ẽ' | 'ẹ' | 'e' => 'e',
        'É' | 'È' | 'Ẻ' | 'Ẽ' | 'Ẹ' | 'E' => 'E',
        'ế' | 'ề' | 'ể' | 'ễ' | 'ệ' | 'ê' => 'ê',
        'Ế' | 'Ề' | 'Ể' | 'Ễ' | 'Ệ' | 'Ê' => 'Ê',
        'í' | 'ì' | 'ỉ' | 'ĩ' | 'ị' | 'i' => 'i',
        'Í' | 'Ì' | 'Ỉ' | 'Ĩ' | 'Ị' | 'I' => 'I',
        'ó' | 'ò' | 'ỏ' | 'õ' | 'ọ' | 'o' => 'o',
        'Ó' | 'Ò' | 'Ỏ' | 'Õ' | 'Ọ' | 'O' => 'O',
        'ố' | 'ồ' | 'ổ' | 'ỗ' | 'ộ' | 'ô' => 'ô',
        'Ố' | 'Ồ' | 'Ổ' | 'Ỗ' | 'Ộ' | 'Ô' => 'Ô',
        'ớ' | 'ờ' | 'ở' | 'ỡ' | 'ợ' | 'ơ' => 'ơ',
        'Ớ' | 'Ờ' | 'Ở' | 'Ỡ' | 'Ợ' | 'Ơ' => 'Ơ',
        'ú' | 'ù' | 'ủ' | 'ũ' | 'ụ' | 'u' => 'u',
        'Ú' | 'Ù' | 'Ủ' | 'Ũ' | 'Ụ' | 'U' => 'U',
        'ứ' | 'ừ' | 'ử' | 'ữ' | 'ự' | 'ư' => 'ư',
        'Ứ' | 'Ừ' | 'Ử' | 'Ữ' | 'Ự' | 'Ư' => 'Ư',
        'ý' | 'ỳ' | 'ỷ' | 'ỹ' | 'ỵ' | 'y' => 'y',
        'Ý' | 'Ỳ' | 'Ỷ' | 'Ỹ' | 'Ỵ' | 'Y' => 'Y',
        _ => return c,
    };

    match (base, tone) {
        ('a', 0) => 'a', ('a', 1) => 'á', ('a', 2) => 'à', ('a', 3) => 'ả', ('a', 4) => 'ã', ('a', 5) => 'ạ',
        ('A', 0) => 'A', ('A', 1) => 'Á', ('A', 2) => 'À', ('A', 3) => 'Ả', ('A', 4) => 'Ã', ('A', 5) => 'Ạ',
        ('ă', 0) => 'ă', ('ă', 1) => 'ắ', ('ă', 2) => 'ằ', ('ă', 3) => 'ẳ', ('ă', 4) => 'ẵ', ('ă', 5) => 'ặ',
        ('Ă', 0) => 'Ă', ('Ă', 1) => 'Ắ', ('Ă', 2) => 'Ằ', ('Ă', 3) => 'Ẳ', ('Ă', 4) => 'Ẵ', ('Ă', 5) => 'Ặ',
        ('â', 0) => 'â', ('â', 1) => 'ấ', ('â', 2) => 'ầ', ('â', 3) => 'ẩ', ('â', 4) => 'ẫ', ('â', 5) => 'ậ',
        ('Â', 0) => 'Â', ('Â', 1) => 'Ấ', ('Â', 2) => 'Ầ', ('Â', 3) => 'Ẩ', ('Â', 4) => 'Ẫ', ('Â', 5) => 'Ậ',
        ('e', 0) => 'e', ('e', 1) => 'é', ('e', 2) => 'è', ('e', 3) => 'ẻ', ('e', 4) => 'ẽ', ('e', 5) => 'ẹ',
        ('E', 0) => 'E', ('E', 1) => 'É', ('E', 2) => 'È', ('E', 3) => 'Ẻ', ('E', 4) => 'Ẽ', ('E', 5) => 'Ẹ',
        ('ê', 0) => 'ê', ('ê', 1) => 'ế', ('ê', 2) => 'ề', ('ê', 3) => 'ể', ('ê', 4) => 'ễ', ('ê', 5) => 'ệ',
        ('Ê', 0) => 'Ê', ('Ê', 1) => 'Ế', ('Ê', 2) => 'Ề', ('Ê', 3) => 'Ể', ('Ê', 4) => 'Ễ', ('Ê', 5) => 'Ệ',
        ('i', 0) => 'i', ('i', 1) => 'í', ('i', 2) => 'ì', ('i', 3) => 'ỉ', ('i', 4) => 'ĩ', ('i', 5) => 'ị',
        ('I', 0) => 'I', ('I', 1) => 'Í', ('I', 2) => 'Ì', ('I', 3) => 'Ỉ', ('I', 4) => 'Ĩ', ('I', 5) => 'Ị',
        ('o', 0) => 'o', ('o', 1) => 'ó', ('o', 2) => 'ò', ('o', 3) => 'ỏ', ('o', 4) => 'õ', ('o', 5) => 'ọ',
        ('O', 0) => 'O', ('O', 1) => 'Ó', ('O', 2) => 'Ò', ('O', 3) => 'Ỏ', ('O', 4) => 'Õ', ('O', 5) => 'Ọ',
        ('ô', 0) => 'ô', ('ô', 1) => 'ố', ('ô', 2) => 'ồ', ('ô', 3) => 'ổ', ('ô', 4) => 'ỗ', ('ô', 5) => 'ộ',
        ('Ô', 0) => 'Ô', ('Ô', 1) => 'Ố', ('Ô', 2) => 'Ồ', ('Ô', 3) => 'Ổ', ('Ô', 4) => 'Ỗ', ('Ô', 5) => 'Ộ',
        ('ơ', 0) => 'ơ', ('ơ', 1) => 'ớ', ('ơ', 2) => 'ờ', ('ơ', 3) => 'ở', ('ơ', 4) => 'ỡ', ('ơ', 5) => 'ợ',
        ('Ơ', 0) => 'Ơ', ('Ơ', 1) => 'Ớ', ('Ơ', 2) => 'Ờ', ('Ơ', 3) => 'Ở', ('Ơ', 4) => 'Ỡ', ('Ơ', 5) => 'Ợ',
        ('u', 0) => 'u', ('u', 1) => 'ú', ('u', 2) => 'ù', ('u', 3) => 'ủ', ('u', 4) => 'ũ', ('u', 5) => 'ụ',
        ('U', 0) => 'U', ('U', 1) => 'Ú', ('U', 2) => 'Ù', ('U', 3) => 'Ủ', ('U', 4) => 'Ũ', ('U', 5) => 'Ụ',
        ('ư', 0) => 'ư', ('ư', 1) => 'ứ', ('ư', 2) => 'ừ', ('ư', 3) => 'ử', ('ư', 4) => 'ữ', ('ư', 5) => 'ự',
        ('Ư', 0) => 'Ư', ('Ư', 1) => 'Ứ', ('Ư', 2) => 'Ừ', ('Ư', 3) => 'Ử', ('Ư', 4) => 'Ữ', ('Ư', 5) => 'Ự',
        ('y', 0) => 'y', ('y', 1) => 'ý', ('y', 2) => 'ỳ', ('y', 3) => 'ỷ', ('y', 4) => 'ỹ', ('y', 5) => 'ỵ',
        ('Y', 0) => 'Y', ('Y', 1) => 'Ý', ('Y', 2) => 'Ỳ', ('Y', 3) => 'Ỷ', ('Y', 4) => 'Ỹ', ('Y', 5) => 'Ỵ',
        _ => c,
    }
}
