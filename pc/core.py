import os, time, base64
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.exceptions import InvalidSignature

b64 = lambda b: base64.b64encode(b).decode()
unb64 = lambda s: base64.b64decode(s)

def new_challenge():
    return os.urandom(32) + int(time.time()).to_bytes(8, "big")

def verify(pub, challenge, sig, max_age=60):
    if time.time() - int.from_bytes(challenge[32:], "big") > max_age:
        return False
    try:
        pub.verify(sig, challenge, ec.ECDSA(hashes.SHA256()))
        return True
    except InvalidSignature:
        return False

def load_pub(path="paired_phone.der"):
    if not os.path.exists(path):
        return None
    return serialization.load_der_public_key(open(path, "rb").read())