export function createTrackingThree(THREE, {
    failDodecahedronGeometry = false,
    failBufferGeometry = false
} = {}) {
    const allocations = [];
    const trackDisposable = (Base) => class extends Base {
        constructor(...args) {
            super(...args);
            const dispose = this.dispose.bind(this);
            this.disposeCount = 0;
            this.dispose = () => {
                this.disposeCount += 1;
                dispose();
            };
            allocations.push(this);
        }
    };
    const TrackedDodecahedronGeometry = trackDisposable(
        THREE.DodecahedronGeometry
    );
    class FailingDodecahedronGeometry extends TrackedDodecahedronGeometry {
        constructor(...args) {
            if (failDodecahedronGeometry) {
                throw new Error("injected rear-field construction failure");
            }
            super(...args);
        }
    }
    const TrackedBufferGeometry = trackDisposable(THREE.BufferGeometry);
    class FailingBufferGeometry extends TrackedBufferGeometry {
        constructor(...args) {
            if (failBufferGeometry) {
                throw new Error("injected rear-field construction failure");
            }
            super(...args);
        }
    }

    return {
        THREE: {
            ...THREE,
            BufferGeometry: failBufferGeometry
                ? FailingBufferGeometry
                : TrackedBufferGeometry,
            BoxGeometry: trackDisposable(THREE.BoxGeometry),
            ConeGeometry: trackDisposable(THREE.ConeGeometry),
            DodecahedronGeometry: failDodecahedronGeometry
                ? FailingDodecahedronGeometry
                : TrackedDodecahedronGeometry,
            ExtrudeGeometry: trackDisposable(THREE.ExtrudeGeometry),
            IcosahedronGeometry: trackDisposable(THREE.IcosahedronGeometry),
            InstancedMesh: trackDisposable(THREE.InstancedMesh),
            TorusGeometry: trackDisposable(THREE.TorusGeometry),
            MeshToonMaterial: trackDisposable(THREE.MeshToonMaterial),
            MeshBasicMaterial: trackDisposable(THREE.MeshBasicMaterial)
        },
        allocations
    };
}
