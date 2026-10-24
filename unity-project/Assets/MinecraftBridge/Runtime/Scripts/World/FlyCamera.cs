using UnityEngine;

namespace Ponte.MinecraftUnity
{
    /// <summary>
    /// Câmera livre estilo Minecraft/Unity editor para navegar no mundo replicado.
    /// WASD + QE sobe/desce, Shift corre, mouse arrasta com botão direito.
    /// </summary>
    [RequireComponent(typeof(Camera))]
    public class FlyCamera : MonoBehaviour
    {
        public float moveSpeed = 10f;
        public float fastMultiplier = 3f;
        public float lookSpeed = 2f;
        public bool invertY = false;

        private Vector2 look;

        void Update()
        {
            // Movimento
            float speed = moveSpeed * (Input.GetKey(KeyCode.LeftShift) ? fastMultiplier : 1f) * Time.deltaTime;
            Vector3 move = Vector3.zero;
            if (Input.GetKey(KeyCode.W)) move += transform.forward;
            if (Input.GetKey(KeyCode.S)) move -= transform.forward;
            if (Input.GetKey(KeyCode.A)) move -= transform.right;
            if (Input.GetKey(KeyCode.D)) move += transform.right;
            if (Input.GetKey(KeyCode.Q) || Input.GetKey(KeyCode.Space)) move += Vector3.up;
            if (Input.GetKey(KeyCode.E) || Input.GetKey(KeyCode.LeftControl)) move -= Vector3.up;
            transform.position += move * speed * 10f;

            // Look com botão direito
            if (Input.GetMouseButton(1))
            {
                Cursor.lockState = CursorLockMode.Locked;
                float mx = Input.GetAxis("Mouse X") * lookSpeed;
                float my = Input.GetAxis("Mouse Y") * lookSpeed * (invertY ? 1 : -1);
                look.x += mx;
                look.y += my;
                look.y = Mathf.Clamp(look.y, -90, 90);
                transform.rotation = Quaternion.Euler(look.y, look.x, 0);
            }
            else if (Input.GetMouseButtonUp(1)) Cursor.lockState = CursorLockMode.None;

            // Scroll zoom
            float scroll = Input.GetAxis("Mouse ScrollWheel");
            if (Mathf.Abs(scroll) > 0.01f) transform.position += transform.forward * scroll * 20f;
        }
    }
}
